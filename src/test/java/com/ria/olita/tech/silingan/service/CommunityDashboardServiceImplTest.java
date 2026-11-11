package com.ria.olita.tech.silingan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.ria.olita.tech.silingan.dto.res.CommunityDashboardResponse;
import com.ria.olita.tech.silingan.dto.res.DashboardMetricResponse;
import com.ria.olita.tech.silingan.dto.res.DashboardReportStatusResponse;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.DirectoryContact;
import com.ria.olita.tech.silingan.entity.DirectoryContactCategory;
import com.ria.olita.tech.silingan.entity.DirectoryContactType;
import com.ria.olita.tech.silingan.entity.IssueStatus;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.UserStatus;
import com.ria.olita.tech.silingan.entity.rbac.CommunityAccess;
import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.AnnouncementRepository;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.DirectoryContactRepository;
import com.ria.olita.tech.silingan.repository.IssueRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.service.impl.CommunityDashboardServiceImpl;

class CommunityDashboardServiceImplTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Manila");

	private final CommunityRepository communityRepository = mock(CommunityRepository.class);
	private final UserCommunityRepository userCommunityRepository = mock(UserCommunityRepository.class);
	private final IssueRepository issueRepository = mock(IssueRepository.class);
	private final AnnouncementRepository announcementRepository = mock(AnnouncementRepository.class);
	private final DirectoryContactRepository directoryContactRepository = mock(DirectoryContactRepository.class);

	private final UUID communityId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		when(communityRepository.existsByIdAndStatus(communityId, CommunityStatus.ACTIVE)).thenReturn(true);
		when(issueRepository.countByStatusForCommunity(communityId)).thenReturn(List.of());
		stubContacts(List.of());
	}

	@SuppressWarnings("unchecked")
	private void stubContacts(List<DirectoryContact> contacts) {
		when(directoryContactRepository.findBy(any(Specification.class), any())).thenReturn(contacts);
	}

	@AfterEach
	void tearDown() {
		UserContextHolder.clear();
	}

	private CommunityDashboardServiceImpl serviceAt(String localDateTime) {
		Clock clock = Clock.fixed(LocalDateTime.parse(localDateTime).atZone(ZONE).toInstant(), ZONE);
		return new CommunityDashboardServiceImpl(
			communityRepository,
			userCommunityRepository,
			issueRepository,
			announcementRepository,
			directoryContactRepository,
			new PermissionService(),
			clock
		);
	}

	private void asAdmin() {
		UserContextHolder.set(UserContext.builder()
			.userId(UUID.randomUUID().toString())
			.communityId(communityId.toString())
			.roles(List.of(SilinganRealmRole.COMMUNITY_ADMIN))
			.communityAccess(new CommunityAccess(communityId, List.of()))
			.build());
	}

	private void asStaff(PermissionEnum... permissions) {
		UserContextHolder.set(UserContext.builder()
			.userId(UUID.randomUUID().toString())
			.communityId(communityId.toString())
			.roles(List.of(SilinganRealmRole.STAFF))
			.communityAccess(new CommunityAccess(communityId, List.of(permissions)))
			.build());
	}

	@Test
	void unknownCommunityIsNotFoundAndNothingElseIsQueried() {
		asAdmin();
		UUID other = UUID.randomUUID();

		assertThatThrownBy(() -> serviceAt("2026-11-11T18:00:00").getDashboard(other))
			.isInstanceOf(NotFoundException.class);

		verify(issueRepository, never()).countByStatusForCommunity(any());
	}

	@Test
	void metricsUseExistingDefinitionsAndOpenReportsFollowStatusGrouping() {
		asAdmin();
		when(userCommunityRepository.countByCommunityIdAndRoleAndUserStatus(
			communityId, SilinganRealmRole.RESIDENT, UserStatus.ACTIVE)).thenReturn(125);
		when(userCommunityRepository.countByCommunityIdAndRoleInAndUserStatus(
			eq(communityId), any(), eq(UserStatus.ACTIVE))).thenReturn(9L);
		when(issueRepository.countByStatusForCommunity(communityId)).thenReturn(List.of(
			new DashboardReportStatusResponse(IssueStatus.OPEN, 4),
			new DashboardReportStatusResponse(IssueStatus.IN_PROGRESS, 3),
			new DashboardReportStatusResponse(IssueStatus.RESOLVED, 21)));

		CommunityDashboardResponse response = serviceAt("2026-11-11T18:00:00").getDashboard(communityId);

		assertThat(response.metrics().activeResidents()).isEqualTo(DashboardMetricResponse.unavailable(125));
		assertThat(response.metrics().activeCommunityStaff()).isEqualTo(DashboardMetricResponse.unavailable(9));
		assertThat(response.metrics().openReports().value()).isEqualTo(7);
		assertThat(response.metrics().openReports().comparisonAvailable()).isFalse();
		assertThat(response.metrics().openReports().percentageChange()).isNull();
	}

	@Test
	void reportStatusIsStableAndZeroFilled() {
		asAdmin();
		when(issueRepository.countByStatusForCommunity(communityId))
			.thenReturn(List.of(new DashboardReportStatusResponse(IssueStatus.OPEN, 2)));

		CommunityDashboardResponse response = serviceAt("2026-11-11T18:00:00").getDashboard(communityId);

		assertThat(response.reportStatus()).extracting(DashboardReportStatusResponse::status)
			.containsExactly(IssueStatus.values());
		assertThat(response.reportStatus()).filteredOn(r -> r.status() != IssueStatus.OPEN)
			.allMatch(r -> r.count() == 0);
	}

	@Test
	void announcementPeriodsAreMonthToDateAgainstEquivalentPreviousElapsedTime() {
		asAdmin();
		when(announcementRepository.countPublishedBetween(eq(communityId), any(), any())).thenReturn(0L);

		serviceAt("2026-11-11T18:00:00").getDashboard(communityId);

		ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
		ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(announcementRepository, org.mockito.Mockito.times(2))
			.countPublishedBetween(eq(communityId), from.capture(), to.capture());

		assertThat(from.getAllValues()).containsExactly(
			LocalDateTime.parse("2026-11-01T00:00:00"), LocalDateTime.parse("2026-10-01T00:00:00"));
		assertThat(to.getAllValues()).containsExactly(
			LocalDateTime.parse("2026-11-11T18:00:00"), LocalDateTime.parse("2026-10-11T18:00:00"));
	}

	@Test
	void yearBoundaryComparesAgainstDecember() {
		asAdmin();

		serviceAt("2027-01-02T06:30:00").getDashboard(communityId);

		verify(announcementRepository).countPublishedBetween(
			communityId, LocalDateTime.parse("2026-12-01T00:00:00"), LocalDateTime.parse("2026-12-02T06:30:00"));
	}

	@Test
	void previousWindowIsCappedAtTheStartOfTheCurrentMonth() {
		asAdmin();

		// 31 March elapsed time exceeds the whole of February.
		serviceAt("2026-03-31T12:00:00").getDashboard(communityId);

		verify(announcementRepository).countPublishedBetween(
			communityId, LocalDateTime.parse("2026-02-01T00:00:00"), LocalDateTime.parse("2026-03-01T00:00:00"));
	}

	@Test
	void timezoneDecidesWhichMonthIsCurrent() {
		asAdmin();
		// 2026-10-31T17:00Z is already 1 November in Manila (UTC+8).
		Clock clock = Clock.fixed(Instant.parse("2026-10-31T17:00:00Z"), ZONE);
		CommunityDashboardServiceImpl service = new CommunityDashboardServiceImpl(
			communityRepository, userCommunityRepository, issueRepository, announcementRepository,
			directoryContactRepository, new PermissionService(), clock);

		service.getDashboard(communityId);

		verify(announcementRepository).countPublishedBetween(
			communityId, LocalDateTime.parse("2026-11-01T00:00:00"), LocalDateTime.parse("2026-11-01T01:00:00"));
	}

	@Test
	void percentageIncreaseDecreaseAndNoChange() {
		assertThat(CommunityDashboardServiceImpl.withComparison(125, 115).percentageChange()).isEqualTo(8.7);
		assertThat(CommunityDashboardServiceImpl.withComparison(14, 15).percentageChange()).isEqualTo(-6.7);
		DashboardMetricResponse same = CommunityDashboardServiceImpl.withComparison(9, 9);
		assertThat(same.percentageChange()).isEqualTo(0.0);
		assertThat(same.comparisonAvailable()).isTrue();
	}

	@Test
	void previousValueOfZeroIsUnavailableRatherThanInfiniteOrFabricated() {
		DashboardMetricResponse metric = CommunityDashboardServiceImpl.withComparison(6, 0);

		assertThat(metric.value()).isEqualTo(6);
		assertThat(metric.percentageChange()).isNull();
		assertThat(metric.comparisonAvailable()).isFalse();
	}

	@Test
	void previewQueriesAreBoundedAndScopedToTheCommunity() {
		asAdmin();

		serviceAt("2026-11-11T18:00:00").getDashboard(communityId);

		ArgumentCaptor<Pageable> announcements = ArgumentCaptor.forClass(Pageable.class);
		verify(announcementRepository).findRecentPublishedForDashboard(eq(communityId), announcements.capture());
		assertThat(announcements.getValue().getPageSize()).isEqualTo(5);

		ArgumentCaptor<Pageable> reports = ArgumentCaptor.forClass(Pageable.class);
		verify(issueRepository).findRecentForDashboard(eq(communityId), reports.capture());
		assertThat(reports.getValue().getPageSize()).isEqualTo(5);
	}

	@Test
	void adminQuickActionsAreAllEnabled() {
		asAdmin();

		var permissions = serviceAt("2026-11-11T18:00:00").getDashboard(communityId).permissions();

		assertThat(permissions.canManageResidents()).isTrue();
		assertThat(permissions.canManageStaff()).isTrue();
		assertThat(permissions.canCreateAnnouncement()).isTrue();
		assertThat(permissions.canViewReports()).isTrue();
		assertThat(permissions.canManageReports()).isTrue();
		assertThat(permissions.canManageDirectory()).isTrue();
	}

	@Test
	void staffQuickActionsAndSectionsFollowGrantedPermissions() {
		asStaff(PermissionEnum.COMMUNITY_VIEW, PermissionEnum.REPORT_VIEW, PermissionEnum.ANNOUNCEMENT_VIEW,
			PermissionEnum.ANNOUNCEMENT_MANAGE);

		CommunityDashboardResponse response = serviceAt("2026-11-11T18:00:00").getDashboard(communityId);

		assertThat(response.permissions().canViewReports()).isTrue();
		assertThat(response.permissions().canManageReports()).isFalse();
		assertThat(response.permissions().canCreateAnnouncement()).isTrue();
		assertThat(response.permissions().canManageStaff()).isFalse();
		assertThat(response.permissions().canManageDirectory()).isFalse();

		// Modules without view access are neither returned nor queried.
		assertThat(response.metrics().activeResidents()).isNull();
		assertThat(response.metrics().activeCommunityStaff()).isNull();
		assertThat(response.directoryContacts()).isEmpty();
		verify(userCommunityRepository, never()).countByCommunityIdAndRoleAndUserStatus(any(), any(), any());
		verify(directoryContactRepository, never()).findBy(any(Specification.class), any());
	}

	@Test
	void staffWithoutReportViewDoesNotTriggerReportQueries() {
		asStaff(PermissionEnum.COMMUNITY_VIEW);

		CommunityDashboardResponse response = serviceAt("2026-11-11T18:00:00").getDashboard(communityId);

		assertThat(response.metrics().openReports()).isNull();
		assertThat(response.reportStatus()).isEmpty();
		assertThat(response.recentReports()).isEmpty();
		verify(issueRepository, never()).countByStatusForCommunity(any());
		verify(issueRepository, never()).findRecentForDashboard(any(), any());
	}

	@Test
	void directoryContactsExposeOnlyPreviewFields() {
		asAdmin();
		UUID contactId = UUID.randomUUID();
		stubContacts(List.of(DirectoryContact.builder()
			.id(contactId)
			.displayName("Barangay Hotline")
			.category(DirectoryContactCategory.EMERGENCY_SERVICE)
			.contactType(DirectoryContactType.values()[0])
			.primaryPhoneNumber("0917 000 0000")
			.secondaryPhoneNumber("private")
			.address("private")
			.build()));

		var contacts = serviceAt("2026-11-11T18:00:00").getDashboard(communityId).directoryContacts();

		assertThat(contacts).hasSize(1);
		assertThat(contacts.get(0).id()).isEqualTo(contactId);
		assertThat(contacts.get(0).emergency()).isTrue();
		assertThat(contacts.get(0).primaryPhoneNumber()).isEqualTo("0917 000 0000");
	}
}
