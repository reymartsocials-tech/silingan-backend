package com.ria.olita.tech.silingan.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.ria.olita.tech.silingan.dto.req.CreateNotificationCommand;
import com.ria.olita.tech.silingan.dto.res.NotificationResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityIssueRouting;
import com.ria.olita.tech.silingan.entity.Issue;
import com.ria.olita.tech.silingan.entity.IssueAssignmentHistory;
import com.ria.olita.tech.silingan.entity.IssueAssignmentSource;
import com.ria.olita.tech.silingan.entity.IssueAssignmentStatus;
import com.ria.olita.tech.silingan.entity.IssueCategory;
import com.ria.olita.tech.silingan.entity.IssueRoutingDestination;
import com.ria.olita.tech.silingan.entity.IssueStatus;
import com.ria.olita.tech.silingan.entity.NotificationType;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.UserStatus;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.event.ReportAssignmentNotificationEvent;
import com.ria.olita.tech.silingan.repository.CommunityIssueRoutingRepository;
import com.ria.olita.tech.silingan.repository.IssueAssignmentHistoryRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class ReportAssignmentServiceImplTest {

	@Mock
	private CommunityIssueRoutingRepository routingRepository;
	@Mock
	private IssueAssignmentHistoryRepository historyRepository;
	@Mock
	private UserCommunityRepository userCommunityRepository;
	@Mock
	private NotificationService notificationService;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	private ReportAssignmentServiceImpl service;

	private final UUID communityId = UUID.randomUUID();
	private IssueCategory parking;
	private Issue issue;

	@BeforeEach
	void setUp() {
		service = new ReportAssignmentServiceImpl(routingRepository, historyRepository, userCommunityRepository,
			new StaffRoutingEligibilityChecker(userCommunityRepository), notificationService, eventPublisher);
		parking = IssueCategory.builder().id(UUID.randomUUID()).name("Parking").communityId(communityId).isActive(true).build();
		issue = Issue.builder()
			.id(UUID.randomUUID())
			.community(Community.builder().id(communityId).build())
			.category(parking)
			.title("Blocked driveway")
			.description("Private resident details")
			.status(IssueStatus.OPEN)
			.build();
		when(historyRepository.save(any(IssueAssignmentHistory.class))).thenAnswer(invocation -> {
			IssueAssignmentHistory history = invocation.getArgument(0);
			history.setId(UUID.randomUUID());
			return history;
		});
	}

	@Test
	void shouldAutoAssignParkingReportToRoutedStaff() {
		UserCommunity staffB = staffMembership(UserStatus.ACTIVE);
		routeParkingTo(staffB);
		stubNotification();

		service.assignOnSubmission(issue);

		assertThat(issue.getAssignmentDestination()).isEqualTo(IssueRoutingDestination.STAFF);
		assertThat(issue.getAssignedStaffMembership()).isSameAs(staffB);
		assertThat(issue.getAssignedTo()).isSameAs(staffB.getUser());
		assertThat(issue.getAssignmentSource()).isEqualTo(IssueAssignmentSource.CATEGORY_ROUTING);
		assertThat(issue.getAssignmentStatus()).isEqualTo(IssueAssignmentStatus.ASSIGNED);

		IssueAssignmentHistory history = capturedHistory();
		assertThat(history.getNewDestination()).isEqualTo(IssueRoutingDestination.STAFF);
		assertThat(history.getNewStaffMembership()).isSameAs(staffB);
		assertThat(history.getAssignmentSource()).isEqualTo(IssueAssignmentSource.CATEGORY_ROUTING);
		assertThat(history.getChangedBy()).isNull();
		assertThat(history.getActivityMessage()).contains("Parking").doesNotContain("Ben");

		ArgumentCaptor<CreateNotificationCommand> command = ArgumentCaptor.forClass(CreateNotificationCommand.class);
		verify(notificationService).createNotification(command.capture());
		assertThat(command.getValue().recipientUserId()).isEqualTo(staffB.getUserId());
		assertThat(command.getValue().type()).isEqualTo(NotificationType.REPORT_ASSIGNED);
		assertThat(command.getValue().body()).doesNotContain("Private resident details");

		ArgumentCaptor<ReportAssignmentNotificationEvent> event = ArgumentCaptor.forClass(ReportAssignmentNotificationEvent.class);
		verify(eventPublisher).publishEvent(event.capture());
		assertThat(event.getValue().reportId()).isEqualTo(issue.getId());
		assertThat(event.getValue().route()).isEqualTo(ReportAssignmentNotificationEvent.ROUTE_REPORT_DETAILS);
	}

	@Test
	void shouldFallBackToPmoQueueWhenRoutedStaffIsInactive() {
		routeParkingTo(staffMembership(UserStatus.INACTIVE));
		UUID triageUserId = UUID.randomUUID();
		when(userCommunityRepository.findActiveTriageUserIdsByCommunityId(communityId)).thenReturn(List.of(triageUserId));
		stubNotification();

		service.assignOnSubmission(issue);

		assertThat(issue.getAssignmentDestination()).isEqualTo(IssueRoutingDestination.PMO_REVIEW_QUEUE);
		assertThat(issue.getAssignedStaffMembership()).isNull();
		assertThat(issue.getAssignedTo()).isNull();
		assertThat(issue.getAssignmentSource()).isEqualTo(IssueAssignmentSource.PMO_FALLBACK);
		assertThat(issue.getAssignmentStatus()).isEqualTo(IssueAssignmentStatus.AWAITING_TRIAGE);
		assertThat(capturedHistory().getActivityMessage())
			.isEqualTo("Your report was received and is waiting for staff assignment.");

		ArgumentCaptor<CreateNotificationCommand> command = ArgumentCaptor.forClass(CreateNotificationCommand.class);
		verify(notificationService).createNotification(command.capture());
		assertThat(command.getValue().recipientUserId()).isEqualTo(triageUserId);
	}

	@Test
	void shouldPreservePreviousAssignmentWhenReassigning() {
		UserCommunity staffB = staffMembership(UserStatus.ACTIVE);
		UserCommunity staffC = staffMembership(UserStatus.ACTIVE);
		issue.setAssignmentDestination(IssueRoutingDestination.STAFF);
		issue.setAssignedStaffMembership(staffB);
		issue.setAssignedTo(staffB.getUser());
		when(userCommunityRepository.findByIdWithUser(staffC.getId())).thenReturn(Optional.of(staffC));
		stubNotification();
		User admin = User.builder().id(UUID.randomUUID()).build();

		service.reassign(issue, IssueRoutingDestination.STAFF, staffC.getId(), " Needs security intervention ", admin);

		IssueAssignmentHistory history = capturedHistory();
		assertThat(history.getPreviousDestination()).isEqualTo(IssueRoutingDestination.STAFF);
		assertThat(history.getPreviousStaffMembership()).isSameAs(staffB);
		assertThat(history.getNewStaffMembership()).isSameAs(staffC);
		assertThat(history.getAssignmentSource()).isEqualTo(IssueAssignmentSource.MANUAL_REASSIGNMENT);
		assertThat(history.getReason()).isEqualTo("Needs security intervention");
		assertThat(history.getChangedBy()).isSameAs(admin);
		assertThat(history.getActivityMessage()).doesNotContain("security intervention");
		assertThat(issue.getAssignedTo()).isSameAs(staffC.getUser());
	}

	private void routeParkingTo(UserCommunity membership) {
		CommunityIssueRouting routing = CommunityIssueRouting.builder()
			.communityId(communityId)
			.categoryId(parking.getId())
			.destinationType(IssueRoutingDestination.STAFF)
			.assignedStaffMembership(membership)
			.active(true)
			.build();
		when(routingRepository.findByCommunityIdAndCategoryId(communityId, parking.getId())).thenReturn(Optional.of(routing));
	}

	private void stubNotification() {
		when(notificationService.createNotification(any(CreateNotificationCommand.class)))
			.thenReturn(NotificationResponse.builder().id(UUID.randomUUID()).build());
	}

	private IssueAssignmentHistory capturedHistory() {
		ArgumentCaptor<IssueAssignmentHistory> captor = ArgumentCaptor.forClass(IssueAssignmentHistory.class);
		verify(historyRepository).save(captor.capture());
		return captor.getValue();
	}

	private UserCommunity staffMembership(UserStatus status) {
		User user = User.builder().id(UUID.randomUUID()).firstName("Ben").lastName("Torres").status(status).build();
		return UserCommunity.builder()
			.id(UUID.randomUUID())
			.user(user)
			.userId(user.getId())
			.communityId(communityId)
			.role(SilinganRealmRole.STAFF)
			.roleCode(StaffRoleCode.MAINTENANCE_ADMIN)
			.build();
	}
}
