package com.ria.olita.tech.silingan.security.context;

import jakarta.persistence.EntityManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;


import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.rbac.CommunityAccess;
import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.service.CommunityRbacService;
import com.ria.olita.tech.silingan.service.CommunityAdminInvitationActivationService;

@Component
@RequiredArgsConstructor
public class UserContextFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(UserContextFilter.class);

	private final UserRepository userRepository;
	private final CommunityRbacService communityRbacService;
	private final CommunityAdminInvitationActivationService invitationActivationService;
	private final EntityManager entityManager;

	private static final Map<String, SilinganRealmRole> ROLE_MAP =
		Arrays.stream(SilinganRealmRole.values())
			.collect(Collectors.toMap(
				r -> r.name()
					.toLowerCase(),
				Function.identity()
			));


	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request,
																	@NonNull HttpServletResponse response,
																	FilterChain filterChain) throws ServletException, IOException {
		try {
			populateContextFromAuthentication();
			enableCommunityFilter();
			filterChain.doFilter(request, response);
		} finally {
			UserContextHolder.clear();
		}
	}

	private void populateContextFromAuthentication() {
		Authentication authentication = SecurityContextHolder.getContext()
			.getAuthentication();

		if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
			log.trace("Skipping UserContext population - authentication is not JwtAuthenticationToken");
			return;
		}

		Map<String, Object> claims = jwtAuth.getToken().getClaims();
		String keycloakUserId = extractStringClaim(claims, "sub");

		if (keycloakUserId == null || keycloakUserId.isBlank()) {
			log.warn("Cannot populate UserContext - no sub claim in token");
			return;
		}

		String userId = userRepository
			.getUserIdByKeycloakUserId(keycloakUserId)
			.orElse(null);
		if (userId == null) {
			log.warn("Cannot populate UserContext - no local user row for keycloakUserId={}", keycloakUserId);
			return;
		}

		String communityId = extractStringClaim(claims, "communityId");
		UUID communityUuid = parseCommunityId(communityId);

		if (communityUuid != null) {
			invitationActivationService.activateIfCompleted(keycloakUserId, communityUuid);
		}

		List<SilinganRealmRole> roles = extractRealmRoles(claims).stream()
			.map(r -> ROLE_MAP.get(r.toLowerCase()))
			.filter(Objects::nonNull)
			.toList();

		CommunityAccess communityAccess = loadCommunityAccess(userId, communityUuid);

		UserContext context = UserContext.builder()
			.userId(userId)
			.keycloakUserId(keycloakUserId)
			.communityId(communityId)
			.roles(roles)
			.communityAccess(communityAccess)
			.build();
		UserContextHolder.set(context);
	}

	private String resolveKeycloakUserId(Authentication authentication) {
		if (authentication instanceof JwtAuthenticationToken jwtAuth) {
			return extractStringClaim(jwtAuth.getToken().getClaims(), "sub");
		}

		if (authentication instanceof OAuth2AuthenticationToken oauth2Auth) {
			if (oauth2Auth.getPrincipal() instanceof OidcUser oidcUser) {
				return extractStringClaim(oidcUser.getClaims(), "sub");
			}
			if (oauth2Auth.getPrincipal() instanceof OAuth2User oauth2User) {
				return extractStringClaim(oauth2User.getAttributes(), "sub");
			}
		}

		return null;
	}

	private CommunityAccess loadCommunityAccess(String userId, UUID communityId) {
		if (communityId == null) {
			return null;
		}
		UUID userUuid = UUID.fromString(userId);
		Set<PermissionEnum> permissions = communityRbacService.resolveEffectivePermissions(userUuid, communityId);
		return new CommunityAccess(communityId, permissions);
	}

	private UUID parseCommunityId(String communityIdClaim) {
		if (communityIdClaim == null || communityIdClaim.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(communityIdClaim);
		} catch (IllegalArgumentException ex) {
			log.warn("Ignoring invalid communityId claim for user context: {}", communityIdClaim);
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private String extractStringClaim(Map<String, Object> claims, String claimName) {
		Object directValue = claims.get(claimName);
		if (directValue instanceof String value) {
			return value;
		}

		Object nestedValue = Optional.ofNullable(claims.get("attributes"))
			.filter(Map.class::isInstance)
			.map(map -> (Map<String, Object>) map)
			.map(map -> map.get(claimName))
			.orElse(null);

		if (nestedValue instanceof String value) {
			return value;
		}

		if (nestedValue instanceof List<?> values && !values.isEmpty()) {
			Object first = values.getFirst();
			return first != null ? first.toString() : null;
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	private List<String> extractRealmRoles(Map<String, Object> claims) {
		Object directRoles = claims.get("roles");
		if (directRoles instanceof List<?> roleList) {
			return roleList.stream()
				.map(String::valueOf)
				.toList();
		}

		return Optional.ofNullable(claims.get("realm_access"))
			.filter(Map.class::isInstance)
			.map(realmAccess -> (Map<String, Object>) realmAccess)
			.map(map -> map.get("roles"))
			.filter(List.class::isInstance)
			.map(list -> (List<String>) list)
			.orElse(List.of());
	}


	private void enableCommunityFilter() {

		UserContext context = UserContextHolder.get();

		if (context == null) {
			log.trace("No user context found, skipping filter");
			return;
		}

		if (UserContextHolder.isPlatformAdmin()) {
			log.trace("Skipping community filter for platform admin");
			return;
		}

		String communityIdStr = context.communityId();

		if (communityIdStr == null) {
			log.warn("Missing communityId - skipping filter");
			return;
		}

		UUID communityId = UUID.fromString(communityIdStr);

		Session session = entityManager.unwrap(Session.class);

		session.enableFilter("communityFilter")
			.setParameter("communityId", communityId);

		log.trace("Community filter enabled for communityId={}", communityId);
	}
}
