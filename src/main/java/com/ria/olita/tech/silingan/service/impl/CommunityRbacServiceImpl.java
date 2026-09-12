package com.ria.olita.tech.silingan.service.impl;

import java.time.LocalDateTime;
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
import com.ria.olita.tech.silingan.dto.res.EffectivePermissionsResponse;
import com.ria.olita.tech.silingan.dto.res.PermissionMatrixResponse;
import com.ria.olita.tech.silingan.dto.res.PermissionMatrixResponse.MatrixCell;
import com.ria.olita.tech.silingan.dto.res.PermissionMatrixResponse.MatrixRole;
import com.ria.olita.tech.silingan.dto.res.PermissionMatrixResponse.MatrixRow;
import com.ria.olita.tech.silingan.dto.res.StaffRoleAssignmentResponse;
import com.ria.olita.tech.silingan.dto.res.StaffRoleResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.UserCommunityStaffRole;
import com.ria.olita.tech.silingan.entity.rbac.AccessLevel;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCatalog;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityStaffRoleRepository;
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
 * <p>Only an <em>active</em> assignment contributes, which makes deactivating an assignment an
 * effective suspension without deleting the record.
 *
 * <p><b>Callers.</b> {@code StaffRoleCatalogController} and {@code StaffRoleAssignmentController}
 * for administration, and {@code UserContextFilter} which calls
 * {@link #resolveEffectivePermissions(UUID, UUID)} on every authenticated request.
 */
@Service
@RequiredArgsConstructor
public class CommunityRbacServiceImpl implements CommunityRbacService {

	private final UserCommunityStaffRoleRepository userCommunityStaffRoleRepository;
	private final UserCommunityRepository userCommunityRepository;
	private final UserRepository userRepository;
	private final CommunityRepository communityRepository;
	private final StaffGrantGuard staffGrantGuard;

	/**
	 * Returns every permission the platform understands, split into its {@code domain} and
	 * {@code action} parts with a human-readable label.
	 */
	@Override
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

	/**
	 * Returns the predefined roles with their name, description, and granted permissions.
	 *
	 * <p>Usage: {@code GET /api/v1/communities/{communityId}/roles}.
	 *
	 * @throws NotFoundException if the community does not exist.
	 */
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

	/**
	 * Returns the permission matrix: roles as columns, modules as rows, each cell carrying
	 * View + Manage, View Only, or No Access.
	 *
	 * <p>Usage: {@code GET /api/v1/communities/{communityId}/roles/permissions}.
	 *
	 * @throws NotFoundException if the community does not exist.
	 */
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

	/**
	 * Assigns or replaces a member's staff role within a community.
	 *
	 * <p>A user holds at most one staff role per community, so this upserts rather than appends.
	 *
	 * <p>Guards applied, in order: the caller may not target themselves; the target must already be a
	 * member of the community; only {@code STAFF} and {@code COMMUNITY_ADMIN} members are eligible;
	 * and the caller may not assign a role carrying permissions they do not themselves hold.
	 *
	 * <p>Usage: {@code PUT /api/v1/communities/{communityId}/staff/{userId}/role}.
	 *
	 * @throws NotFoundException  if the community, user, or membership does not exist.
	 * @throws ForbiddenException if the member is ineligible, or the caller is escalating privileges
	 *                            or modifying their own account.
	 */
	@Override
	@Transactional
	public StaffRoleAssignmentResponse assignStaffRole(UUID communityId, UUID userId, AssignStaffRoleRequest request) {
		Community community = communityRepository.findById(communityId)
			.orElseThrow(() -> new NotFoundException("Community not found"));
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException("User not found"));

		staffGrantGuard.assertNotSelf(userId, "change the staff role");

		UserCommunity membership = userCommunityRepository.findByUserIdAndCommunityId(userId, communityId)
			.orElseThrow(() -> new NotFoundException("User is not a member of the community"));

		if (membership.getRole() != SilinganRealmRole.STAFF && membership.getRole() != SilinganRealmRole.COMMUNITY_ADMIN) {
			throw new ForbiddenException("Staff role can only be assigned to STAFF or COMMUNITY_ADMIN members");
		}

		staffGrantGuard.assertCanAssignRole(
			communityId,
			request.roleCode(),
			StaffRoleCatalog.permissions(request.roleCode()),
			() -> resolveEffectivePermissions(staffGrantGuard.requireCurrentUserId(), communityId)
		);

		UserCommunityStaffRole assignment = userCommunityStaffRoleRepository
			.findByUserIdAndCommunityId(userId, communityId)
			.orElseGet(() -> UserCommunityStaffRole.builder()
				.user(user)
				.community(community)
				.build());

		assignment.setRoleCode(request.roleCode());
		assignment.setActive(request.active());
		assignment.setAssignedAt(LocalDateTime.now());
		assignment.setAssignedBy(resolveCurrentUserEntity().orElse(null));

		return toAssignmentResponse(userCommunityStaffRoleRepository.save(assignment));
	}

	/**
	 * Reads a member's staff role assignment, including inactive ones.
	 *
	 * <p>Returns an empty assignment rather than throwing when the user has none, so the admin UI can
	 * render "no role assigned" without special-casing a 404.
	 *
	 * <p>Usage: {@code GET /api/v1/communities/{communityId}/staff/{userId}/role}.
	 */
	@Override
	@Transactional(readOnly = true)
	public StaffRoleAssignmentResponse getStaffRoleAssignment(UUID communityId, UUID userId) {
		ensureCommunityExists(communityId);
		ensureUserExists(userId);

		return userCommunityStaffRoleRepository.findByUserIdAndCommunityId(userId, communityId)
			.map(this::toAssignmentResponse)
			.orElse(new StaffRoleAssignmentResponse(userId, communityId, null, false, null, null));
	}

	/**
	 * Returns a member's assigned role and the permissions that role grants.
	 *
	 * <p>Usage: {@code GET /api/v1/communities/{communityId}/staff/{userId}/effective-permissions}.
	 */
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

	/**
	 * Returns the authenticated caller's own role and permissions for a community, so the frontend
	 * can decide what to render. Not an authorization decision — that is always re-made server-side
	 * by {@code PermissionAspect}.
	 *
	 * <p>Usage: {@code GET /api/v1/me/communities/{communityId}/capabilities}.
	 */
	@Override
	@Transactional(readOnly = true)
	public CurrentUserCapabilitiesResponse getCurrentUserCapabilities(UUID communityId) {
		ensureCommunityExists(communityId);

		UserContext userContext = UserContextHolder.get();
		if (userContext == null || userContext.userId() == null) {
			throw new ForbiddenException("No authenticated user context");
		}

		UUID userId;
		try {
			userId = UUID.fromString(userContext.userId());
		} catch (IllegalArgumentException ex) {
			throw new ForbiddenException("Invalid authenticated user context");
		}

		if (UserContextHolder.isPlatformAdmin() || UserContextHolder.isCommunityAdmin()) {
			return new CurrentUserCapabilitiesResponse(
				userId,
				communityId,
				StaffRoleCode.COMMUNITY_ADMIN,
				StaffRoleCatalog.permissions(StaffRoleCode.COMMUNITY_ADMIN)
			);
		}

		return new CurrentUserCapabilitiesResponse(
			userId,
			communityId,
			resolveAssignedRole(userId, communityId).orElse(null),
			resolveEffectivePermissions(userId, communityId)
		);
	}

	/**
	 * Resolves the flat set of permissions a user holds in a community.
	 *
	 * <p><b>Hot path.</b> {@code UserContextFilter} calls this once per authenticated request, so it
	 * is deliberately lean — a single lookup plus a static map read.
	 *
	 * @return the role's permission set, or empty when no active role is assigned.
	 */
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

	/**
	 * Returns the code of the user's <em>active</em> staff role in a community, if any. Inactive
	 * assignments are ignored, because a suspended role is not the user's current one.
	 */
	@Override
	@Transactional(readOnly = true)
	public Optional<StaffRoleCode> resolveAssignedRole(UUID userId, UUID communityId) {
		return userCommunityStaffRoleRepository.findByUserIdAndCommunityIdAndActiveTrue(userId, communityId)
			.map(UserCommunityStaffRole::getRoleCode);
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

	/**
	 * Maps a persisted assignment to its API response, reading identifiers from the shadow columns
	 * where available so no lazy association has to be initialised.
	 */
	private StaffRoleAssignmentResponse toAssignmentResponse(UserCommunityStaffRole assignment) {
		UUID userId = assignment.getUserId() != null
			? assignment.getUserId()
			: (assignment.getUser() != null ? assignment.getUser().getId() : null);
		UUID communityId = assignment.getCommunityId() != null
			? assignment.getCommunityId()
			: (assignment.getCommunity() != null ? assignment.getCommunity().getId() : null);

		return new StaffRoleAssignmentResponse(
			userId,
			communityId,
			assignment.getRoleCode(),
			assignment.getActive(),
			assignment.getAssignedAt(),
			assignment.getAssignedBy() != null ? assignment.getAssignedBy().getId() : null
		);
	}

	/**
	 * Derives a display label such as "Manage Announcements" from a permission's module and action.
	 */
	private String buildPermissionDescription(PermissionEnum permission) {
		String action = permission.getAction().canManage() ? "Manage" : "View";
		return action + " " + permission.getDomain().getLabel();
	}

	private void ensureCommunityExists(UUID communityId) {
		if (!communityRepository.existsById(communityId)) {
			throw new NotFoundException("Community not found");
		}
	}

	private void ensureUserExists(UUID userId) {
		if (!userRepository.existsById(userId)) {
			throw new NotFoundException("User not found");
		}
	}

	/**
	 * Loads the authenticated caller's {@link User} entity for audit stamping. Returns empty rather
	 * than throwing, because it only populates {@code assignedBy} — the authorization decision was
	 * already made by {@link StaffGrantGuard}.
	 */
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
}
