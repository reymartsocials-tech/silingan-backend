package com.ria.olita.tech.silingan.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.res.CurrentUserCommunityPermissionsResponse;
import com.ria.olita.tech.silingan.dto.res.CurrentUserResponse;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.security.scope.StaffGrantGuard;
import com.ria.olita.tech.silingan.service.CommunityRbacService;
import com.ria.olita.tech.silingan.service.CurrentUserService;

import lombok.RequiredArgsConstructor;

/**
 * Serves {@code GET /api/v1/me}: the caller's profile joined with the realm roles resolved by
 * {@code UserContextFilter} and the per-community staff role and permissions resolved by
 * {@link CommunityRbacService}.
 *
 * <p>This is a read-only aggregation; it grants nothing and is therefore open to every
 * authenticated role.
 */
@Service
@RequiredArgsConstructor
public class CurrentUserServiceImpl implements CurrentUserService {

	private final UserRepository userRepository;
	private final CommunityRbacService communityRbacService;
	private final StaffGrantGuard staffGrantGuard;
	private final CommunityRepository communityRepository;

	@Override
	@Transactional(readOnly = true)
	public CurrentUserResponse getCurrentUser() {
		UUID userId = staffGrantGuard.requireCurrentUserId();
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException("User not found"));

		UserContext context = UserContextHolder.get();
		UUID selectedCommunityId = Optional.ofNullable(context)
			.map(UserContext::communityId)
			.map(UUID::fromString)
			.orElse(null);

		List<CurrentUserCommunityPermissionsResponse> communities = communityRbacService.getCurrentUserCommunities();

		CurrentUserCommunityPermissionsResponse selected = communities.stream()
			.filter(CurrentUserCommunityPermissionsResponse::selected)
			.findFirst()
			.orElse(null);

		return new CurrentUserResponse(
			user.getId(),
			user.getKeycloakUserId(),
			user.getUsername(),
			user.getEmail(),
			user.getMobileNumber(),
			user.getFirstName(),
			user.getLastName(),
			user.getStatus(),
			context != null && context.roles() != null ? context.roles() : List.of(),
			selectedCommunityId,
			selected != null ? selected.roleCode() : null,
			selected != null ? selected.permissions() : Set.of(),
			communityRepository.findActiveCommunityNameMapByUserId(userId)
		);
	}
}
