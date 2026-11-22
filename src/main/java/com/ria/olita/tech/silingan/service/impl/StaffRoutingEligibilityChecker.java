package com.ria.olita.tech.silingan.service.impl;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ria.olita.tech.silingan.entity.IssueRoutingDestination;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.UserStatus;
import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCatalog;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.exception.ConflictException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;

import lombok.RequiredArgsConstructor;

/**
 * Server-side authority on whether a community staff membership may receive routed reports.
 */
@Component
@RequiredArgsConstructor
public class StaffRoutingEligibilityChecker {

	private final UserCommunityRepository userCommunityRepository;

	public boolean isEligible(UserCommunity membership, UUID communityId) {
		if (membership == null || communityId == null || !communityId.equals(membership.getCommunityId())) {
			return false;
		}
		if (membership.getRole() != SilinganRealmRole.STAFF && membership.getRole() != SilinganRealmRole.COMMUNITY_ADMIN) {
			return false;
		}
		User user = membership.getUser();
		if (user == null || user.getStatus() != UserStatus.ACTIVE || Boolean.TRUE.equals(user.getDeleted())) {
			return false;
		}
		StaffRoleCode roleCode = resolveRoleCode(membership);
		return roleCode != null && StaffRoleCatalog.permissions(roleCode).contains(PermissionEnum.REPORT_MANAGE);
	}

	/**
	 * Loads a membership and requires it to be eligible. Memberships of other communities are
	 * reported as not found so that their existence is not disclosed.
	 */
	public UserCommunity requireEligible(UUID communityId, UUID membershipId) {
		UserCommunity membership = userCommunityRepository.findByIdWithUser(membershipId)
			.filter(candidate -> communityId.equals(candidate.getCommunityId()))
			.orElseThrow(() -> new NotFoundException("Staff membership not found"));
		if (!isEligible(membership, communityId)) {
			throw new ConflictException("Staff member is not eligible to handle reports");
		}
		return membership;
	}

	/** Enforces that STAFF destinations carry a membership and PMO destinations do not. */
	public static void validateDestination(IssueRoutingDestination destination, UUID staffMembershipId) {
		if (destination == null) {
			throw new ValidationException("Destination type is required");
		}
		if (destination == IssueRoutingDestination.STAFF && staffMembershipId == null) {
			throw new ValidationException("staffMembershipId is required when destination type is STAFF");
		}
		if (destination == IssueRoutingDestination.PMO_REVIEW_QUEUE && staffMembershipId != null) {
			throw new ValidationException("staffMembershipId must not be provided when destination type is PMO_REVIEW_QUEUE");
		}
	}

	public static StaffRoleCode resolveRoleCode(UserCommunity membership) {
		if (membership.getRoleCode() != null) {
			return membership.getRoleCode();
		}
		if (membership.getRole() == SilinganRealmRole.COMMUNITY_ADMIN) {
			return StaffRoleCode.COMMUNITY_ADMIN;
		}
		return null;
	}

	/** Resident-safe display name: first name plus last-name initial. */
	public static String displayName(User user) {
		if (user == null) {
			return null;
		}
		String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
		String last = user.getLastName() == null ? "" : user.getLastName().trim();
		if (last.isEmpty()) {
			return first.isEmpty() ? "Staff" : first;
		}
		return (first + " " + last.charAt(0) + ".").trim();
	}

	public static String designation(UserCommunity membership) {
		StaffRoleCode roleCode = resolveRoleCode(membership);
		return roleCode == null ? "Staff" : roleCode.displayName();
	}
}
