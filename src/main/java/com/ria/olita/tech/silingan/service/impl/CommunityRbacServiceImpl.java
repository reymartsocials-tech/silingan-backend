package com.ria.olita.tech.silingan.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.req.AssignStaffRoleRequest;
import com.ria.olita.tech.silingan.dto.res.AvailablePermissionsResponse;
import com.ria.olita.tech.silingan.dto.res.AvailablePermissionsResponse.PermissionInfo;
import com.ria.olita.tech.silingan.dto.res.CurrentUserCapabilitiesResponse;
import com.ria.olita.tech.silingan.dto.res.CurrentUserCommunityPermissionsResponse;
import com.ria.olita.tech.silingan.dto.res.EffectivePermissionsResponse;
import com.ria.olita.tech.silingan.dto.res.PermissionMatrixResponse;
import com.ria.olita.tech.silingan.dto.res.PermissionMatrixResponse.MatrixCell;
import com.ria.olita.tech.silingan.dto.res.PermissionMatrixResponse.MatrixRole;
import com.ria.olita.tech.silingan.dto.res.PermissionMatrixResponse.MatrixRow;
import com.ria.olita.tech.silingan.dto.res.StaffRoleAssignmentResponse;
import com.ria.olita.tech.silingan.dto.res.StaffRoleResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.UserStatus;
import com.ria.olita.tech.silingan.entity.rbac.AccessLevel;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCatalog;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.security.scope.StaffGrantGuard;
import com.ria.olita.tech.silingan.service.CommunityRbacService;

import lombok.RequiredArgsConstructor;

/**
 * Community-scoped RBAC over a fixed, code-owned role catalogue.
 *
 * <p><b>Permission model.</b> A staff member's authority in a community is exactly the permission
 * set of the single predefined role assigned to them in that community, as declared by
 * {@link StaffRoleCatalog}. For MVP there are no custom roles, no custom permissions, no
 * per-community overrides, and no per-user grants — the catalogue is read-only, so what the
 * permission matrix shows is always what is enforced.
 *
 * <p>A staff role assignment is represented by populating the roleCode field in UserCommunity.
 * When roleCode is NULL, the user has no staff role in that community.
 *
 * <p><b>Callers.</b> {@code StaffRoleCatalogController} and {@code StaffRoleAssignmentController}
 * for administration, and {@code UserContextFilter} which calls
 * {@link #resolveEffectivePermissions(UUID, UUID)} on every authenticated request.
 */
@Service
@RequiredArgsConstructor
public class CommunityRbacServiceImpl implements CommunityRbacService {

	private final UserCommunityRepository userCommunityRepository;
	private final UserRepository userRepository;
	private final CommunityRepository communityRepository;
	private final StaffGrantGuard staffGrantGuard;

	@Override
	@Transactional(readOnly = true)
	public AvailablePermissionsResponse getPermissionCatalog() {
		List<PermissionInfo> permissions = Arrays.stream(PermissionEnum.values())
			.map(permission -> new PermissionInfo(
				permission,
				permission.getDomain().getValue(),
				permission.getAction().value(),
				buildPermissionDescription(permission)
			))
			.toList();
		return new AvailablePermissionsResponse(permissions);
	}

	@Override
	@Transactional(readOnly = true)
	public List<StaffRoleResponse> getRoleCatalog(UUID communityId) {
		ensureCommunityExists(communityId);

		return StaffRoleCatalog.roles().stream()
			.map(roleCode -> new StaffRoleResponse(
				roleCode,
				roleCode.displayName(),
				roleCode.description(),
				StaffRoleCatalog.isHighestAccess(roleCode),
				StaffRoleCatalog.permissions(roleCode)
			))
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public PermissionMatrixResponse getPermissionMatrix(UUID communityId) {
		ensureCommunityExists(communityId);

		List<MatrixRole> roles = StaffRoleCatalog.roles().stream()
			.map(roleCode -> new MatrixRole(
				roleCode,
				roleCode.displayName(),
				StaffRoleCatalog.isHighestAccess(roleCode)
			))
			.toList();

		List<MatrixRow> modules = StaffRoleCatalog.modules().stream()
			.map(this::buildMatrixRow)
			.toList();

		return new PermissionMatrixResponse(roles, modules);
	}

	@Override
	@Transactional
	public StaffRoleAssignmentResponse assignStaffRole(UUID communityId, UUID userId, AssignStaffRoleRequest request) {
		Community community = communityRepository.findById(communityId)
			.orElseThrow(() -> new NotFoundException("Community not found"));
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException("User not found"));

		staffGrantGuard.assertNotSelf(userId, "change the staff role");

		UserCommunity membership = userCommunityRepository.findByUserIdAndCommunityIdAndUserStatusActive(userId, communityId)
			.orElseThrow(() -> new NotFoundException("User is not an active member of the community"));

		if (membership.getRole() != SilinganRealmRole.STAFF && membership.getRole() != SilinganRealmRole.COMMUNITY_ADMIN) {
			throw new ForbiddenException("Staff role can only be assigned to STAFF or COMMUNITY_ADMIN members");
		}

		if (membership.getRole() == SilinganRealmRole.COMMUNITY_ADMIN && request.roleCode() != StaffRoleCode.COMMUNITY_ADMIN) {
			throw new ForbiddenException("The final Community Admin cannot be assigned a lower role");
		}

		staffGrantGuard.assertCanAssignRole(
			communityId,
			request.roleCode(),
			StaffRoleCatalog.permissions(request.roleCode()),
			() -> resolveEffectivePermissions(staffGrantGuard.requireCurrentUserId(), communityId)
		);

		membership.setRoleCode(request.roleCode());
		membership.setAssignedAt(LocalDateTime.now(ZoneOffset.UTC));
		membership.setAssignedBy(resolveCurrentUserEntity().orElse(null));

		return toAssignmentResponse(userCommunityRepository.save(membership));
	}

	@Override
	@Transactional(readOnly = true)
	public StaffRoleAssignmentResponse getStaffRoleAssignment(UUID communityId, UUID userId) {
		ensureCommunityExists(communityId);
		ensureUserExists(userId);

		return userCommunityRepository.findByUserIdAndCommunityIdWithStaffRole(userId, communityId)
			.map(this::toAssignmentResponse)
			.orElse(new StaffRoleAssignmentResponse(userId, communityId, null, null, null));
	}

	@Override
	@Transactional(readOnly = true)
	public EffectivePermissionsResponse getEffectivePermissions(UUID communityId, UUID userId) {
		ensureCommunityExists(communityId);
		ensureUserExists(userId);

		Optional<StaffRoleCode> roleCode = resolveAssignedRole(userId, communityId);

		return new EffectivePermissionsResponse(
			userId,
			communityId,
			roleCode.orElse(null),
			roleCode.map(StaffRoleCatalog::permissions).orElse(Set.of())
		);
	}

	@Override
	@Transactional(readOnly = true)
	public CurrentUserCapabilitiesResponse getCurrentUserCapabilities(UUID communityId) {
		ensureCommunityExists(communityId);
		
		UUID userId = staffGrantGuard.requireCurrentUserId();
		UserCommunity membership = userCommunityRepository.findByUserIdAndCommunityIdAndUserStatusActive(userId, communityId)
			.orElse(null);

		if (membership == null) {
			return new CurrentUserCapabilitiesResponse(userId, communityId, null, Set.of());
		}

		StaffRoleCode roleCode = resolveCommunityRoleCode(membership, membership.getRoleCode());
		return new CurrentUserCapabilitiesResponse(
			userId,
			communityId,
			roleCode,
			roleCode == null ? Set.of() : StaffRoleCatalog.permissions(roleCode)
		);
	}

	@Override
	@Transactional(readOnly = true)
	public List<CurrentUserCommunityPermissionsResponse> getCurrentUserCommunities() {
		UUID userId = staffGrantGuard.requireCurrentUserId();
		UUID selectedCommunityId = Optional.ofNullable(UserContextHolder.get())
			.map(UserContext::communityId)
			.map(UUID::fromString)
			.orElse(null);

		List<UserCommunity> memberships = userCommunityRepository.findActiveByUserIdWithCommunity(userId);

		return memberships.stream()
			.map(membership -> {
				UUID communityId = membership.getCommunity().getId();
				StaffRoleCode roleCode = resolveCommunityRoleCode(membership, membership.getRoleCode());
				return new CurrentUserCommunityPermissionsResponse(
					communityId,
					membership.getCommunity().getCommunityCode(),
					membership.getCommunity().getName(),
					communityId.equals(selectedCommunityId),
					roleCode,
					roleCode == null ? Set.of() : StaffRoleCatalog.permissions(roleCode)
				);
			})
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Set<PermissionEnum> resolveEffectivePermissions(UUID userId, UUID communityId) {
		return resolveAssignedRole(userId, communityId)
			.map(StaffRoleCatalog::permissions)
			.map(permissions -> permissions.isEmpty()
				? EnumSet.noneOf(PermissionEnum.class)
				: EnumSet.copyOf(permissions))
			.map(permissions -> (Set<PermissionEnum>) permissions)
			.orElseGet(() -> EnumSet.noneOf(PermissionEnum.class));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<StaffRoleCode> resolveAssignedRole(UUID userId, UUID communityId) {
		return userCommunityRepository.findByUserIdAndCommunityIdWithStaffRole(userId, communityId)
			.map(UserCommunity::getRoleCode);
	}

	private MatrixRow buildMatrixRow(Domain module) {
		List<MatrixCell> cells = StaffRoleCatalog.roles().stream()
			.map(roleCode -> {
				AccessLevel level = StaffRoleCatalog.accessLevel(roleCode, module);
				return new MatrixCell(roleCode, level, level.label());
			})
			.toList();

		return new MatrixRow(module.getValue(), module.getLabel(), cells);
	}

	private StaffRoleAssignmentResponse toAssignmentResponse(UserCommunity membership) {
		UUID userId = membership.getUserId() != null
			? membership.getUserId()
			: (membership.getUser() != null ? membership.getUser().getId() : null);
		UUID communityId = membership.getCommunityId() != null
			? membership.getCommunityId()
			: (membership.getCommunity() != null ? membership.getCommunity().getId() : null);

		return new StaffRoleAssignmentResponse(
			userId,
			communityId,
			membership.getRoleCode(),
			membership.getAssignedAt(),
			membership.getAssignedBy() != null ? membership.getAssignedBy().getId() : null
		);
	}

	private String buildPermissionDescription(PermissionEnum permission) {
		String action = permission.getAction().canManage() ? "Manage" : "View";
		return action + " " + permission.getDomain().getLabel();
	}

	private void ensureCommunityExists(UUID communityId) {
		if (!communityRepository.existsByIdAndStatus(communityId, CommunityStatus.ACTIVE)) {
			throw new NotFoundException("Community not found");
		}
	}

	private void ensureUserExists(UUID userId) {
		if (!userRepository.existsByIdAndStatus(userId, UserStatus.ACTIVE)) {
			throw new NotFoundException("User not found");
		}
	}

	private Optional<User> resolveCurrentUserEntity() {
		UserContext context = UserContextHolder.get();
		if (context == null || context.userId() == null) {
			return Optional.empty();
		}

		try {
			return userRepository.findById(UUID.fromString(context.userId()));
		} catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	private StaffRoleCode resolveCommunityRoleCode(UserCommunity membership, StaffRoleCode assignedRole) {
		if (assignedRole != null) {
			return assignedRole;
		}
		if (membership.getRole() == SilinganRealmRole.COMMUNITY_ADMIN) {
			return StaffRoleCode.COMMUNITY_ADMIN;
		}
		return null;
	}
}
