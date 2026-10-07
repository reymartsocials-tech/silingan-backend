package com.ria.olita.tech.silingan.security.context;

import static com.ria.olita.tech.silingan.entity.InvitationStatus.PENDING;
import static com.ria.olita.tech.silingan.entity.rbac.StaffRoleCatalog.roles;

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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;


import com.ria.olita.tech.silingan.entity.Invitation;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserStatus;
import com.ria.olita.tech.silingan.entity.rbac.CommunityAccess;
import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.service.CommunityRbacService;
import com.ria.olita.tech.silingan.service.InviteService;
import com.ria.olita.tech.silingan.service.KeycloakService;

@Component
@RequiredArgsConstructor
public class UserContextFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(UserContextFilter.class);

	private final UserRepository userRepository;
	private final CommunityRbacService communityRbacService;
	private final InviteService inviteService;
	private final KeycloakService keycloakService;
	private final InvitationActivationGate activationGate;
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

		Map<String, Object> claims = jwtAuth.getToken()
			.getClaims();
		String keycloakUserId = extractStringClaim(claims, "sub");

		if (keycloakUserId == null || keycloakUserId.isBlank()) {
			log.warn("Cannot populate UserContext - no sub claim in token");
			return;
		}

		// Invitation activation is performed once at login after Keycloak actions complete.
		// The gate keeps it off the hot path once the identity has nothing left to activate.
		boolean checkInvitations = activationGate.needsCheck(keycloakUserId);
		String email = extractStringClaim(claims, "email");
		
		boolean activationHappened = false;
		if (checkInvitations) {
			activationHappened = activateInvitationsIfAny(keycloakUserId, email);
		}

		User user = userRepository.findByKeycloakUserId(keycloakUserId)
			.orElse(null);
		if (user == null) {
			log.warn("Cannot populate UserContext - no local user row for keycloakUserId={}", keycloakUserId);
			return;
		}
		UUID communityId = user.getSelectedCommunityId();

		if (checkInvitations) {
			// Only mark as SETTLED if activation actually happened.
			// If nothing was activated (due to incomplete Keycloak actions), mark as UNSETTLED
			// so the next request will try again.
			activationGate.record(keycloakUserId, 
				activationHappened ? InvitationActivationGate.Outcome.SETTLED 
					: InvitationActivationGate.Outcome.UNSETTLED);
		}

		List<String> rawRoles = extractRealmRoles(claims);
		List<SilinganRealmRole> roles = rawRoles.stream()
			.map(r -> ROLE_MAP.get(r.toLowerCase()))
			.filter(Objects::nonNull)
			.toList();
		
		if (!rawRoles.isEmpty() || !roles.isEmpty()) {
			log.debug("User roles extracted - raw: {}, mapped: {}", rawRoles, roles);
		}

		CommunityAccess communityAccess = loadCommunityAccess(user.getId()
			.toString(), communityId);


		UserContext context = UserContext.builder()
			.userId(user.getId()
				.toString())
			.keycloakUserId(keycloakUserId)
			// Must stay null when absent. String.valueOf(null) yields the literal text "null",
			// which slips past null checks and then blows up in UUID.fromString.
			.communityId(communityId != null ? communityId.toString() : null)
			.roles(roles)
			.communityAccess(communityAccess)
			.build();
		UserContextHolder.set(context);
	}

	/**
	 * Activate pending invitations for this identity when Keycloak required actions are complete.
	 * Invitations include both admin and staff types. The activation is unified through the InviteService.
	 * Also syncs profile data from Keycloak on first login to ensure DB has latest firstName, lastName, mobileNumber.
	 * 
	 * Note: With onboarding persistence, the User record always exists by this point (created at invitation time),
	 * so we always have a selected community to pass to the activation service.
	 * 
	 * @return true if any invitation was actually activated, false if activation was skipped
	 */
	private boolean activateInvitationsIfAny(String keycloakUserId, String email) {
		try {
			User user = userRepository.findByKeycloakUserId(keycloakUserId).orElse(null);
			if (user == null) {
				log.warn("Cannot activate invitations - no user found for keycloakUserId={}", keycloakUserId);
				return false;
			}
			
			UUID communityId = user.getSelectedCommunityId();
			boolean activated = inviteService.activateInvitation(keycloakUserId, communityId, email);
			
			if (activated) {
				// Sync profile from Keycloak on first login
				syncProfileFromKeycloak(user, keycloakUserId);
			}
			
			return activated;
		} catch (RuntimeException ex) {
			log.error("Invitation activation failed for keycloakUserId={}", keycloakUserId, ex);
			return false;
		}
	}

	/**
	 * Sync profile data (firstName, lastName, mobileNumber, email) from Keycloak to local User record.
	 * Called on first login to ensure DB has latest profile information.
	 */
	private void syncProfileFromKeycloak(User user, String keycloakUserId) {
		try {
			Map<String, String> profile = keycloakService.getUserProfile(keycloakUserId);
			if (profile == null || profile.isEmpty()) {
				log.debug("No profile data retrieved from Keycloak for user {}", keycloakUserId);
				return;
			}

			// Update user fields with Keycloak data
			boolean updated = false;
			
			String kcFirstName = profile.get("firstName");
			if (kcFirstName != null && !kcFirstName.equals(user.getFirstName())) {
				user.setFirstName(kcFirstName);
				updated = true;
			}
			
			String kcLastName = profile.get("lastName");
			if (kcLastName != null && !kcLastName.equals(user.getLastName())) {
				user.setLastName(kcLastName);
				updated = true;
			}
			
			String kcMobileNumber = profile.get("mobileNumber");
			if (kcMobileNumber != null && !kcMobileNumber.equals(user.getMobileNumber())) {
				user.setMobileNumber(kcMobileNumber);
				updated = true;
			}
			
			String kcEmail = profile.get("email");
			if (kcEmail != null && !kcEmail.equals(user.getEmail())) {
				user.setEmail(kcEmail);
				updated = true;
			}
			
			if (updated) {
				user.setStatus(UserStatus.ACTIVE);
				userRepository.save(user);
				log.info("Profile synced from Keycloak for user {}", keycloakUserId);
			}
		} catch (Exception ex) {
			log.warn("Failed to sync profile from Keycloak for user {}", keycloakUserId, ex);
		}
	}

	private CommunityAccess loadCommunityAccess(String userId, UUID communityId) {		if (communityId == null) {
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


	/**
	 * Enables Hibernate's {@code communityFilter} for the rest of the request.
	 *
	 * <p><b>Depends on {@code spring.jpa.open-in-view=true}.</b> The filter is enabled on the
	 * session bound to the request by OpenSessionInView; without that binding this call would
	 * resolve to a throwaway session and the tenant isolation on Announcement, Issue and Media
	 * would silently stop applying. Turning open-in-view off requires moving this enablement
	 * inside the transaction boundary first.
	 */
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

		// Tolerant parse rather than UUID.fromString: a malformed value must downgrade to
		// "no community scope", never abort the request with a 500.
		UUID communityId = parseCommunityId(communityIdStr);

		if (communityId == null) {
			log.warn("Missing or invalid communityId ({}) - skipping community filter", communityIdStr);
			return;
		}

		Session session = entityManager.unwrap(Session.class);

		session.enableFilter("communityFilter")
			.setParameter("communityId", communityId);

		log.trace("Community filter enabled for communityId={}", communityId);
	}
}
