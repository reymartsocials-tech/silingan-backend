package com.ria.olita.tech.silingan.security.scope;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Policy checks that stop a caller from granting more authority than they hold, or from editing
 * their own authority.
 *
 * <p>The caller's own permission set is supplied lazily so that this component stays free of a
 * dependency on the RBAC service (which itself depends on these checks).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaffGrantGuard {

	private final CommunityScopeGuard communityScopeGuard;

	/**
	 * Platform admins, and community admins acting inside their own community, may grant anything.
	 */
	public boolean hasUnrestrictedGrant(UUID communityId) {
		if (UserContextHolder.isPlatformAdmin()) {
			return true;
		}
		return UserContextHolder.isCommunityAdmin() && communityScopeGuard.hasAccess(communityId);
	}

	public UUID requireCurrentUserId() {
		UserContext context = UserContextHolder.get();
		if (context == null || context.userId() == null) {
			throw new ForbiddenException("No authenticated user context");
		}
		try {
			return UUID.fromString(context.userId());
		} catch (IllegalArgumentException ex) {
			throw new ForbiddenException("Invalid authenticated user context");
		}
	}

	/**
	 * Blocks self-service privilege changes. Platform admins are exempt because they operate outside
	 * any single community and locking them out of their own account has no security benefit.
	 */
	public void assertNotSelf(UUID targetUserId, String action) {
		if (UserContextHolder.isPlatformAdmin()) {
			return;
		}
		if (targetUserId != null && targetUserId.equals(requireCurrentUserId())) {
			throw new ForbiddenException("You cannot " + action + " for your own account");
		}
	}

	/**
	 * Enforces that {@code requested} is a subset of what the caller currently holds.
	 */
	public void assertCanGrant(
		UUID communityId,
		Set<PermissionEnum> requested,
		Supplier<Set<PermissionEnum>> callerPermissions
	) {
		if (requested == null || requested.isEmpty() || hasUnrestrictedGrant(communityId)) {
			return;
		}

		EnumSet<PermissionEnum> held = toEnumSet(callerPermissions.get());
		EnumSet<PermissionEnum> escalated = toEnumSet(requested);
		escalated.removeAll(held);

		if (!escalated.isEmpty()) {
			log.warn(
				"Privilege escalation blocked: user={} community={} attemptedToGrant={}",
				requireCurrentUserId(), communityId, escalated
			);
			throw new ForbiddenException(
				"You cannot grant permissions you do not hold: "
					+ escalated.stream().map(PermissionEnum::getValue).sorted().collect(Collectors.joining(", "))
			);
		}
	}

	/**
	 * Assigning the COMMUNITY_ADMIN staff role is reserved for callers with unrestricted grant
	 * authority; any other role must be fully covered by the caller's own permissions.
	 */
	public void assertCanAssignRole(
		UUID communityId,
		StaffRoleCode roleCode,
		Set<PermissionEnum> rolePermissions,
		Supplier<Set<PermissionEnum>> callerPermissions
	) {
		if (hasUnrestrictedGrant(communityId)) {
			return;
		}

		if (roleCode == StaffRoleCode.COMMUNITY_ADMIN) {
			throw new ForbiddenException("Only a community or platform administrator may assign the COMMUNITY_ADMIN role");
		}

		assertCanGrant(communityId, rolePermissions, callerPermissions);
	}

	private EnumSet<PermissionEnum> toEnumSet(Set<PermissionEnum> permissions) {
		return (permissions == null || permissions.isEmpty())
			? EnumSet.noneOf(PermissionEnum.class)
			: EnumSet.copyOf(permissions);
	}
}
