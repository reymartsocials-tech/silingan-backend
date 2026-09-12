package com.ria.olita.tech.silingan.service.impl;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.res.CommunityStaffMemberResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityStaffStatus;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.UserCommunityStaffRole;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityStaffRoleRepository;
import com.ria.olita.tech.silingan.service.KeycloakService;
import com.ria.olita.tech.silingan.service.StaffDirectoryService;

import lombok.RequiredArgsConstructor;

/**
 * Read model for the community staff directory: who the staff are, which predefined role they hold,
 * and whether their account is enabled.
 */
@Service
@RequiredArgsConstructor
public class StaffDirectoryServiceImpl implements StaffDirectoryService {

	private final CommunityRepository communityRepository;
	private final UserCommunityRepository userCommunityRepository;
	private final UserCommunityStaffRoleRepository userCommunityStaffRoleRepository;
	private final KeycloakService keycloakService;

	@Override
	@Transactional(readOnly = true)
	public List<CommunityStaffMemberResponse> getCommunityStaffDirectory(
			UUID communityId,
			String searchTerm,
			StaffRoleCode role,
			CommunityStaffStatus status) {
		if (!communityRepository.existsById(communityId)) {
			throw new NotFoundException("Community not found");
		}

		Set<SilinganRealmRole> staffMembershipRoles = EnumSet.of(SilinganRealmRole.STAFF, SilinganRealmRole.COMMUNITY_ADMIN);

		List<UserCommunity> staffMembers = userCommunityRepository.findStaffByCommunityWithFilters(
			communityId,
			staffMembershipRoles,
			normalizeSearchTerm(searchTerm)
		);

		Map<UUID, StaffRoleCode> assignedRoleByUserId = resolveAssignedRoleByUserId(communityId, staffMembers);

		return staffMembers.stream()
			.map(member -> toStaffDirectoryResponse(member, resolveDirectoryRole(member, assignedRoleByUserId)))
			.filter(member -> role == null || member.role() == role)
			.filter(member -> status == null || member.status() == status)
			.toList();
	}

	private String normalizeSearchTerm(String searchTerm) {
		if (searchTerm == null || searchTerm.isBlank()) {
			return null;
		}
		return searchTerm.trim();
	}

	private CommunityStaffMemberResponse toStaffDirectoryResponse(UserCommunity userCommunity, StaffRoleCode roleCode) {
		User user = userCommunity.getUser();
		CommunityStaffStatus status = keycloakService.isUserEnabled(user.getKeycloakUserId())
			? CommunityStaffStatus.ACTIVE
			: CommunityStaffStatus.INACTIVE;

		return new CommunityStaffMemberResponse(
			user.getId(),
			buildFullName(user),
			roleCode,
			user.getEmail(),
			user.getMobileNumber(),
			status
		);
	}

	private Map<UUID, StaffRoleCode> resolveAssignedRoleByUserId(UUID communityId, List<UserCommunity> staffMembers) {
		if (staffMembers.isEmpty()) {
			return Map.of();
		}

		List<UUID> userIds = staffMembers.stream()
			.map(member -> member.getUser().getId())
			.toList();

		Map<UUID, StaffRoleCode> assignedRoleByUserId = new HashMap<>();
		for (UserCommunityStaffRole assignment : userCommunityStaffRoleRepository
				.findByCommunityIdAndUserIdInAndActiveTrue(communityId, userIds)) {
			assignedRoleByUserId.put(assignment.getUserId(), assignment.getRoleCode());
		}
		return assignedRoleByUserId;
	}

	private StaffRoleCode resolveDirectoryRole(UserCommunity userCommunity, Map<UUID, StaffRoleCode> assignedRoleByUserId) {
		StaffRoleCode assignedRole = assignedRoleByUserId.get(userCommunity.getUser().getId());
		if (assignedRole != null) {
			return assignedRole;
		}
		if (userCommunity.getRole() == SilinganRealmRole.COMMUNITY_ADMIN) {
			return StaffRoleCode.COMMUNITY_ADMIN;
		}
		return null;
	}

	private String buildFullName(User user) {
		String fullName = (Objects.toString(user.getFirstName(), "") + " " + Objects.toString(user.getLastName(), "")).trim();
		return fullName.isEmpty() ? user.getUsername() : fullName;
	}
}
