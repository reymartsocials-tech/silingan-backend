package com.ria.olita.tech.silingan.service.impl;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.res.CommunityStaffMemberResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityStaffStatus;
import com.ria.olita.tech.silingan.dto.res.StaffDetailsResponse;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
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
	private final KeycloakService keycloakService;

	@Override
	@Transactional(readOnly = true)
	public Page<CommunityStaffMemberResponse> getCommunityStaffDirectory(
			UUID communityId,
			String searchTerm,
			StaffRoleCode role,
			CommunityStaffStatus status,
			Pageable pageable) {
		if (!communityRepository.existsById(communityId)) {
			throw new NotFoundException("Community not found");
		}

		Set<SilinganRealmRole> staffMembershipRoles = EnumSet.of(SilinganRealmRole.STAFF, SilinganRealmRole.COMMUNITY_ADMIN);

		List<UserCommunity> staffMembers = userCommunityRepository.findStaffByCommunityWithFilters(
			communityId,
			staffMembershipRoles,
			normalizeSearchTerm(searchTerm)
		);

		List<CommunityStaffMemberResponse> filtered = staffMembers.stream()
			.map(member -> toStaffDirectoryResponse(member, resolveDirectoryRole(member)))
			.filter(member -> role == null || member.role() == role)
			.filter(member -> status == null || member.status() == status)
			.toList();

		int start = (int) pageable.getOffset();
		int end = Math.min((start + pageable.getPageSize()), filtered.size());
		List<CommunityStaffMemberResponse> pageContent = filtered.subList(start, end);

		return new PageImpl<>(pageContent, pageable, filtered.size());
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

	private StaffRoleCode resolveDirectoryRole(UserCommunity userCommunity) {
		if (userCommunity.getRoleCode() != null) {
			return userCommunity.getRoleCode();
		}
		if (userCommunity.getRole() == SilinganRealmRole.COMMUNITY_ADMIN) {
			return StaffRoleCode.COMMUNITY_ADMIN;
		}
		return null;
	}

	@Override
	@Transactional(readOnly = true)
	public StaffDetailsResponse getStaffMember(UUID communityId, UUID staffId) {
		if (!communityRepository.existsById(communityId)) {
			throw new NotFoundException("Community not found");
		}

		UserCommunity userCommunity = userCommunityRepository.findByUserIdAndCommunityIdWithDetails(staffId, communityId)
			.orElseThrow(() -> new NotFoundException("Staff member not found"));

		Set<SilinganRealmRole> staffMembershipRoles = EnumSet.of(SilinganRealmRole.STAFF, SilinganRealmRole.COMMUNITY_ADMIN);
		if (!staffMembershipRoles.contains(userCommunity.getRole())) {
			throw new NotFoundException("Staff member not found");
		}

		User user = userCommunity.getUser();
		StaffRoleCode roleCode = resolveDirectoryRole(userCommunity);

		CommunityStaffStatus status = keycloakService.isUserEnabled(user.getKeycloakUserId())
			? CommunityStaffStatus.ACTIVE
			: CommunityStaffStatus.INACTIVE;

		return new StaffDetailsResponse(
			user.getId(),
			user.getFirstName(),
			user.getLastName(),
			user.getEmail(),
			user.getMobileNumber(),
			roleCode,
			userCommunity.getJoinedAt(),
			user.getStatus(),
			null,
			user.getCreatedAt(),
			user.getUpdatedAt(),
			null,
			null,
			null,
			null,
			user.getCreatedBy() != null ? buildFullName(user.getCreatedBy()) : null,
			user.getUpdatedBy() != null ? buildFullName(user.getUpdatedBy()) : null
		);
	}

	private String buildFullName(User user) {
		String fullName = (Objects.toString(user.getFirstName(), "") + " " + Objects.toString(user.getLastName(), "")).trim();
		return fullName.isEmpty() ? user.getUsername() : fullName;
	}
}
