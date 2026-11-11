package com.ria.olita.tech.silingan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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

import com.ria.olita.tech.silingan.dto.req.PlatformGrowthPeriod;
import com.ria.olita.tech.silingan.dto.res.PlatformAttentionIssue;
import com.ria.olita.tech.silingan.dto.res.PlatformDashboardResponse;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.InvitationType;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.repository.AttentionCommunityRow;
import com.ria.olita.tech.silingan.repository.CommunityMonthlyCount;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.CommunityStatusCount;
import com.ria.olita.tech.silingan.repository.InvitationRepository;
import com.ria.olita.tech.silingan.repository.PlatformActivityRow;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.service.impl.PlatformDashboardServiceImpl;

class PlatformDashboardServiceImplTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Manila");

	private CommunityRepository communities;
	private UserCommunityRepository memberships;
	private InvitationRepository invitations;
	private PlatformDashboardServiceImpl service;

	@BeforeEach
	void setUp() {
		communities = mock(CommunityRepository.class);
		memberships = mock(UserCommunityRepository.class);
		invitations = mock(InvitationRepository.class);
		Clock clock = Clock.fixed(Instant.parse("2026-03-15T04:00:00Z"), ZONE);
		service = new PlatformDashboardServiceImpl(communities, memberships, invitations, clock);
		UserContextHolder.set(UserContext.builder()
			.userId(UUID.randomUUID().toString())
			.roles(List.of(SilinganRealmRole.PLATFORM_ADMIN))
			.build());
	}

	@AfterEach
	void tearDown() {
		UserContextHolder.clear();
	}

	@Test
	void growthIsZeroFilledAscendingAcrossYearBoundary() {
		when(communities.countCreatedPerMonth(any(), any()))
			.thenReturn(List.of(new CommunityMonthlyCount(2025, 12, 4), new CommunityMonthlyCount(2026, 3, 2)));

		PlatformDashboardResponse six = service.getDashboard(PlatformGrowthPeriod.SIX_MONTHS);
		assertThat(six.communityGrowth()).extracting("period")
			.containsExactly("2025-10", "2025-11", "2025-12", "2026-01", "2026-02", "2026-03");
		assertThat(six.communityGrowth()).extracting("value").containsExactly(0L, 0L, 4L, 0L, 0L, 2L);

		assertThat(service.getDashboard(PlatformGrowthPeriod.TWELVE_MONTHS).communityGrowth()).hasSize(12);
	}

	@Test
	void statusGroupsAreMutuallyExclusiveAndSumToTotal() {
		when(communities.countByStatusWithAdmin()).thenReturn(List.of(
			new CommunityStatusCount(CommunityStatus.ACTIVE, 10, 8),
			new CommunityStatusCount(CommunityStatus.DRAFT, 5, 1),
			new CommunityStatusCount(CommunityStatus.ARCHIVE, 3, 2)));

		var summary = service.getDashboard(PlatformGrowthPeriod.SIX_MONTHS).communitiesByStatus();

		assertThat(summary.total()).isEqualTo(18);
		assertThat(summary.active()).isEqualTo(8);
		assertThat(summary.needsAdmin()).isEqualTo(6);
		assertThat(summary.inactive()).isEqualTo(4);
		assertThat(summary.active() + summary.needsAdmin() + summary.inactive()).isEqualTo(summary.total());
	}

	@Test
	void onlyPendingInvitationsHaveComparisonAndZeroPreviousIsUnavailable() {
		when(invitations.countPendingByType(eq(InvitationType.ADMIN), any(), any())).thenReturn(3L);
		when(invitations.countPendingAt(eq(InvitationType.ADMIN), any())).thenReturn(2L);
		var metrics = service.getDashboard(PlatformGrowthPeriod.SIX_MONTHS).metrics();

		assertThat(metrics.pendingInvitations().percentageChange()).isEqualTo(50.0);
		assertThat(metrics.pendingInvitations().comparisonAvailable()).isTrue();
		assertThat(metrics.totalCommunities().comparisonAvailable()).isFalse();
		assertThat(metrics.totalResidents().percentageChange()).isNull();
		assertThat(metrics.totalAdmins().comparisonAvailable()).isFalse();

		when(invitations.countPendingAt(eq(InvitationType.ADMIN), any())).thenReturn(0L);
		var zero = service.getDashboard(PlatformGrowthPeriod.SIX_MONTHS).metrics().pendingInvitations();
		assertThat(zero.percentageChange()).isNull();
		assertThat(zero.comparisonAvailable()).isFalse();
	}

	@Test
	void attentionRowsCarryPrioritizedIssues() {
		UUID id = UUID.randomUUID();
		when(communities.findNeedingAttention(any(), any())).thenReturn(List.of(
			new AttentionCommunityRow(id, "Garden", CommunityStatus.DRAFT, 9, 0, 1),
			new AttentionCommunityRow(id, "Old", CommunityStatus.ARCHIVE, 1, 1, 0)));

		var rows = service.getDashboard(PlatformGrowthPeriod.SIX_MONTHS).communitiesNeedingAttention();

		assertThat(rows.get(0).issue()).isEqualTo(PlatformAttentionIssue.NO_ADMIN);
		assertThat(rows.get(0).issues()).containsExactly(PlatformAttentionIssue.NO_ADMIN,
			PlatformAttentionIssue.INACTIVE_COMMUNITY, PlatformAttentionIssue.ADMIN_INVITATION_PENDING);
		assertThat(rows.get(1).issue()).isEqualTo(PlatformAttentionIssue.INACTIVE_COMMUNITY);
	}

	@Test
	void recentActivitiesAreMergedNewestFirstAndLimitedToFive() {
		LocalDateTime base = LocalDateTime.of(2026, 3, 10, 0, 0);
		when(communities.findRecentlyCreated(any())).thenReturn(List.of(row("A", base.plusDays(1)), row("B", base.plusDays(3))));
		when(invitations.findRecentInvited(eq(InvitationType.ADMIN), any()))
			.thenReturn(List.of(row("C", base.plusDays(2)), row("D", base)));
		when(invitations.findRecentAccepted(eq(InvitationType.ADMIN), any())).thenReturn(List.of(row("E", base.plusDays(4))));
		when(invitations.findRecentRevoked(eq(InvitationType.ADMIN), any())).thenReturn(List.of(row("F", base.minusDays(1))));

		var activities = service.getDashboard(PlatformGrowthPeriod.SIX_MONTHS).recentActivities();

		assertThat(activities).hasSize(5);
		assertThat(activities).extracting("description").containsExactly("E", "B", "C", "A", "D");
		assertThat(activities.get(0).id()).startsWith("INVITATION_ACCEPTED:");
	}

	@Test
	void emptyPlatformReturnsEmptyCollectionsAndZeroes() {
		PlatformDashboardResponse r = service.getDashboard(PlatformGrowthPeriod.SIX_MONTHS);
		assertThat(r.recentActivities()).isEmpty();
		assertThat(r.communitiesNeedingAttention()).isEmpty();
		assertThat(r.communitiesByStatus().total()).isZero();
		assertThat(r.metrics().pendingInvitations().comparisonAvailable()).isFalse();
	}

	@Test
	void permissionsFollowPlatformAdminRole() {
		assertThat(service.getDashboard(PlatformGrowthPeriod.SIX_MONTHS).permissions().canCreateCommunity()).isTrue();
	}

	private static PlatformActivityRow row(String name, LocalDateTime at) {
		return new PlatformActivityRow(UUID.randomUUID(), UUID.randomUUID(), name, at);
	}
}
