package com.ria.olita.tech.silingan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.ria.olita.tech.silingan.dto.req.CreateReportRequest;
import com.ria.olita.tech.silingan.dto.req.UpdateReportRequest;
import com.ria.olita.tech.silingan.dto.res.ReportResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.Issue;
import com.ria.olita.tech.silingan.entity.IssueCategory;
import com.ria.olita.tech.silingan.entity.IssueStatus;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.rbac.Action;
import com.ria.olita.tech.silingan.entity.rbac.CommunityAccess;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.IssueCategoryRepository;
import com.ria.olita.tech.silingan.repository.IssueRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.service.impl.ReportServiceImpl;

class ReportServiceImplTest {

	@AfterEach
	void tearDown() {
		UserContextHolder.clear();
	}

	@Test
	void shouldSubmitReportForCommunityMember() {
		IssueRepository issueRepository = Mockito.mock(IssueRepository.class);
		IssueCategoryRepository issueCategoryRepository = Mockito.mock(IssueCategoryRepository.class);
		CommunityRepository communityRepository = Mockito.mock(CommunityRepository.class);
		UserRepository userRepository = Mockito.mock(UserRepository.class);
		UserCommunityRepository userCommunityRepository = Mockito.mock(UserCommunityRepository.class);

		ReportServiceImpl service = new ReportServiceImpl(
			issueRepository,
			issueCategoryRepository,
			communityRepository,
			userRepository,
			userCommunityRepository
		);

		UUID userId = UUID.randomUUID();
		UUID communityId = UUID.randomUUID();
		UUID categoryId = UUID.randomUUID();

		UserContextHolder.set(UserContext.builder()
			.userId(userId.toString())
			.communityId(communityId.toString())
			.roles(List.of(SilinganRealmRole.RESIDENT))
			.communityAccess(new CommunityAccess(communityId, List.of()))
			.build());

		User user = User.builder().id(userId).username("resident").firstName("Juan").lastName("Dela Cruz").build();
		Community community = Community.builder().id(communityId).build();
		IssueCategory category = IssueCategory.builder().id(categoryId).name("Plumbing").community(community).communityId(communityId).isActive(true).build();
		UserCommunity membership = UserCommunity.builder().user(user).community(community).role(SilinganRealmRole.RESIDENT).build();

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));
		when(communityRepository.findById(communityId)).thenReturn(Optional.of(community));
		when(userCommunityRepository.findByUserIdAndCommunityId(userId, communityId)).thenReturn(Optional.of(membership));
		when(issueCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
		when(issueRepository.save(Mockito.any(Issue.class))).thenAnswer(invocation -> {
			Issue issue = invocation.getArgument(0);
			issue.setId(UUID.randomUUID());
			return issue;
		});

		ReportResponse response = service.submitReport(new CreateReportRequest(
			communityId,
			"Water Leak",
			"Leak in hallway",
			categoryId,
			null,
			null,
			"Tower A Hallway",
			null,
			null
		));

		assertThat(response.id()).isNotNull();
		assertThat(response.communityId()).isEqualTo(communityId);
		assertThat(response.status()).isEqualTo(IssueStatus.OPEN);
	}

	@Test
	void shouldReturnOwnReportsForResidentWithoutReportViewPermission() {
		IssueRepository issueRepository = Mockito.mock(IssueRepository.class);
		IssueCategoryRepository issueCategoryRepository = Mockito.mock(IssueCategoryRepository.class);
		CommunityRepository communityRepository = Mockito.mock(CommunityRepository.class);
		UserRepository userRepository = Mockito.mock(UserRepository.class);
		UserCommunityRepository userCommunityRepository = Mockito.mock(UserCommunityRepository.class);

		ReportServiceImpl service = new ReportServiceImpl(
			issueRepository,
			issueCategoryRepository,
			communityRepository,
			userRepository,
			userCommunityRepository
		);

		UUID userId = UUID.randomUUID();
		UUID communityId = UUID.randomUUID();
		UserContextHolder.set(UserContext.builder()
			.userId(userId.toString())
			.communityId(communityId.toString())
			.roles(List.of(SilinganRealmRole.RESIDENT))
			.communityAccess(new CommunityAccess(communityId, List.of()))
			.build());

		Community community = Community.builder().id(communityId).build();
		User user = User.builder().id(userId).username("resident").build();
		IssueCategory category = IssueCategory.builder().id(UUID.randomUUID()).name("Plumbing").communityId(communityId).build();
		Issue issue = Issue.builder()
			.id(UUID.randomUUID())
			.community(community)
			.communityId(communityId)
			.reporter(user)
			.category(category)
			.title("Leak")
			.description("Leak desc")
			.status(IssueStatus.OPEN)
			.build();

		when(communityRepository.findById(communityId)).thenReturn(Optional.of(community));
		when(userCommunityRepository.findByUserIdAndCommunityId(userId, communityId))
			.thenReturn(Optional.of(UserCommunity.builder().role(SilinganRealmRole.RESIDENT).build()));
		when(issueRepository.findByReporterIdAndCommunityId(userId, communityId, PageRequest.of(0, 10)))
			.thenReturn(new PageImpl<>(List.of(issue), PageRequest.of(0, 10), 1));

		var page = service.getReports(communityId, null, PageRequest.of(0, 10));
		assertThat(page.getTotalElements()).isEqualTo(1);
	}

	@Test
	void shouldRejectDetailsForNonOwnerResidentWithoutViewPermission() {
		IssueRepository issueRepository = Mockito.mock(IssueRepository.class);
		IssueCategoryRepository issueCategoryRepository = Mockito.mock(IssueCategoryRepository.class);
		CommunityRepository communityRepository = Mockito.mock(CommunityRepository.class);
		UserRepository userRepository = Mockito.mock(UserRepository.class);
		UserCommunityRepository userCommunityRepository = Mockito.mock(UserCommunityRepository.class);

		ReportServiceImpl service = new ReportServiceImpl(
			issueRepository,
			issueCategoryRepository,
			communityRepository,
			userRepository,
			userCommunityRepository
		);

		UUID currentUserId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		UUID communityId = UUID.randomUUID();
		UUID reportId = UUID.randomUUID();

		UserContextHolder.set(UserContext.builder()
			.userId(currentUserId.toString())
			.communityId(communityId.toString())
			.roles(List.of(SilinganRealmRole.RESIDENT))
			.communityAccess(new CommunityAccess(communityId, List.of()))
			.build());

		Issue issue = Issue.builder()
			.id(reportId)
			.communityId(communityId)
			.reporter(User.builder().id(otherUserId).username("other").build())
			.category(IssueCategory.builder().id(UUID.randomUUID()).name("Cat").build())
			.title("T")
			.description("D")
			.status(IssueStatus.OPEN)
			.build();

		when(issueRepository.findById(reportId)).thenReturn(Optional.of(issue));
		when(userCommunityRepository.findByUserIdAndCommunityId(currentUserId, communityId))
			.thenReturn(Optional.of(UserCommunity.builder().role(SilinganRealmRole.RESIDENT).build()));

		assertThatThrownBy(() -> service.getReportDetails(reportId))
			.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void shouldAllowStaffWithManagePermissionToUpdateStatus() {
		IssueRepository issueRepository = Mockito.mock(IssueRepository.class);
		IssueCategoryRepository issueCategoryRepository = Mockito.mock(IssueCategoryRepository.class);
		CommunityRepository communityRepository = Mockito.mock(CommunityRepository.class);
		UserRepository userRepository = Mockito.mock(UserRepository.class);
		UserCommunityRepository userCommunityRepository = Mockito.mock(UserCommunityRepository.class);

		ReportServiceImpl service = new ReportServiceImpl(
			issueRepository,
			issueCategoryRepository,
			communityRepository,
			userRepository,
			userCommunityRepository
		);

		UUID staffId = UUID.randomUUID();
		UUID communityId = UUID.randomUUID();
		UUID reportId = UUID.randomUUID();
		CommunityAccess access = new CommunityAccess(communityId, List.of(PermissionEnum.of(Domain.REPORT, Action.MANAGE)));
		UserContextHolder.set(UserContext.builder()
			.userId(staffId.toString())
			.communityId(communityId.toString())
			.roles(List.of(SilinganRealmRole.STAFF))
			.communityAccess(access)
			.build());

		Issue issue = Issue.builder()
			.id(reportId)
			.communityId(communityId)
			.reporter(User.builder().id(UUID.randomUUID()).build())
			.category(IssueCategory.builder().id(UUID.randomUUID()).name("Cat").communityId(communityId).build())
			.title("Old")
			.description("Old")
			.status(IssueStatus.OPEN)
			.build();

		when(issueRepository.findById(reportId)).thenReturn(Optional.of(issue));
		when(userCommunityRepository.findByUserIdAndCommunityId(staffId, communityId))
			.thenReturn(Optional.of(UserCommunity.builder().role(SilinganRealmRole.STAFF).build()));
		when(issueRepository.save(Mockito.any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ReportResponse updated = service.updateReport(reportId, new UpdateReportRequest(
			null, null, null, null, null, null, null, null, IssueStatus.IN_PROGRESS
		));

		assertThat(updated.status()).isEqualTo(IssueStatus.IN_PROGRESS);
	}

	@Test
	void shouldCancelOwnReportForResident() {
		IssueRepository issueRepository = Mockito.mock(IssueRepository.class);
		IssueCategoryRepository issueCategoryRepository = Mockito.mock(IssueCategoryRepository.class);
		CommunityRepository communityRepository = Mockito.mock(CommunityRepository.class);
		UserRepository userRepository = Mockito.mock(UserRepository.class);
		UserCommunityRepository userCommunityRepository = Mockito.mock(UserCommunityRepository.class);

		ReportServiceImpl service = new ReportServiceImpl(
			issueRepository,
			issueCategoryRepository,
			communityRepository,
			userRepository,
			userCommunityRepository
		);

		UUID userId = UUID.randomUUID();
		UUID communityId = UUID.randomUUID();
		UUID reportId = UUID.randomUUID();

		UserContextHolder.set(UserContext.builder()
			.userId(userId.toString())
			.communityId(communityId.toString())
			.roles(List.of(SilinganRealmRole.RESIDENT))
			.communityAccess(new CommunityAccess(communityId, List.of()))
			.build());

		Issue issue = Issue.builder()
			.id(reportId)
			.communityId(communityId)
			.reporter(User.builder().id(userId).build())
			.category(IssueCategory.builder().id(UUID.randomUUID()).name("Cat").build())
			.title("Sample")
			.description("Sample")
			.status(IssueStatus.OPEN)
			.build();

		when(issueRepository.findById(reportId)).thenReturn(Optional.of(issue));
		when(userCommunityRepository.findByUserIdAndCommunityId(userId, communityId))
			.thenReturn(Optional.of(UserCommunity.builder().role(SilinganRealmRole.RESIDENT).build()));
		when(issueRepository.save(Mockito.any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ReportResponse cancelled = service.cancelReport(reportId);
		assertThat(cancelled.status()).isEqualTo(IssueStatus.CANCELLED);
	}
}
