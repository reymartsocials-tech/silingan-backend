package com.ria.olita.tech.silingan.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ria.olita.tech.silingan.dto.req.UpsertIssueRoutingRequest;
import com.ria.olita.tech.silingan.dto.res.IssueRoutingResponse;
import com.ria.olita.tech.silingan.dto.res.IssueRoutingStatus;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityIssueRouting;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.IssueCategory;
import com.ria.olita.tech.silingan.entity.IssueRoutingDestination;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.UserStatus;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.CommunityIssueRoutingRepository;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.IssueCategoryRepository;
import com.ria.olita.tech.silingan.repository.IssueRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;

@ExtendWith(MockitoExtension.class)
class IssueRoutingServiceImplTest {

	@Mock
	private CommunityIssueRoutingRepository routingRepository;
	@Mock
	private CommunityRepository communityRepository;
	@Mock
	private IssueCategoryRepository issueCategoryRepository;
	@Mock
	private IssueRepository issueRepository;
	@Mock
	private UserCommunityRepository userCommunityRepository;

	private IssueRoutingServiceImpl service;

	private final UUID communityId = UUID.randomUUID();
	private final UUID parkingId = UUID.randomUUID();
	private Community community;
	private IssueCategory parking;

	@BeforeEach
	void setUp() {
		service = new IssueRoutingServiceImpl(routingRepository, communityRepository, issueCategoryRepository,
			issueRepository, userCommunityRepository, new StaffRoutingEligibilityChecker(userCommunityRepository));
		community = Community.builder().id(communityId).build();
		parking = IssueCategory.builder().id(parkingId).name("Parking").communityId(communityId).isActive(true).build();
		when(communityRepository.findByIdAndStatus(communityId, CommunityStatus.ACTIVE)).thenReturn(Optional.of(community));
		when(issueCategoryRepository.findById(parkingId)).thenReturn(Optional.of(parking));
	}

	@Test
	void shouldRouteParkingToEligibleStaffOfSameCommunity() {
		UserCommunity staffB = staffMembership(communityId);
		when(userCommunityRepository.findByIdWithUser(staffB.getId())).thenReturn(Optional.of(staffB));
		when(routingRepository.findByCommunityIdAndCategoryId(communityId, parkingId)).thenReturn(Optional.empty());
		when(routingRepository.saveAndFlush(any(CommunityIssueRouting.class))).thenAnswer(invocation -> invocation.getArgument(0));

		IssueRoutingResponse response = service.upsertRouting(communityId, parkingId,
			new UpsertIssueRoutingRequest(IssueRoutingDestination.STAFF, staffB.getId()));

		assertThat(response.destinationType()).isEqualTo(IssueRoutingDestination.STAFF);
		assertThat(response.routingStatus()).isEqualTo(IssueRoutingStatus.ACTIVE);
		assertThat(response.assignedStaff().staffMembershipId()).isEqualTo(staffB.getId());
		assertThat(response.assignedStaff().displayName()).isEqualTo("Ben T.");
		assertThat(response.assignedStaff().designation()).isEqualTo("PMO Staff");
	}

	@Test
	void shouldRejectStaffMembershipFromAnotherCommunity() {
		UserCommunity foreignStaff = staffMembership(UUID.randomUUID());
		when(userCommunityRepository.findByIdWithUser(foreignStaff.getId())).thenReturn(Optional.of(foreignStaff));

		assertThatThrownBy(() -> service.upsertRouting(communityId, parkingId,
			new UpsertIssueRoutingRequest(IssueRoutingDestination.STAFF, foreignStaff.getId())))
			.isInstanceOf(NotFoundException.class);
		verify(routingRepository, never()).saveAndFlush(any());
	}

	private UserCommunity staffMembership(UUID membershipCommunityId) {
		User user = User.builder().id(UUID.randomUUID()).firstName("Ben").lastName("Torres").status(UserStatus.ACTIVE).build();
		return UserCommunity.builder()
			.id(UUID.randomUUID())
			.user(user)
			.userId(user.getId())
			.communityId(membershipCommunityId)
			.role(SilinganRealmRole.STAFF)
			.roleCode(StaffRoleCode.PMO_STAFF)
			.build();
	}
}
