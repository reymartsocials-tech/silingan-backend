package com.ria.olita.tech.silingan.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.req.PlatformGrowthPeriod;
import com.ria.olita.tech.silingan.dto.res.DashboardMetricResponse;
import com.ria.olita.tech.silingan.dto.res.PlatformActivityResponse;
import com.ria.olita.tech.silingan.dto.res.PlatformActivityType;
import com.ria.olita.tech.silingan.dto.res.PlatformAttentionCommunityResponse;
import com.ria.olita.tech.silingan.dto.res.PlatformAttentionIssue;
import com.ria.olita.tech.silingan.dto.res.PlatformCommunityStatusSummaryResponse;
import com.ria.olita.tech.silingan.dto.res.PlatformDashboardMetricsResponse;
import com.ria.olita.tech.silingan.dto.res.PlatformDashboardPermissionsResponse;
import com.ria.olita.tech.silingan.dto.res.PlatformDashboardResponse;
import com.ria.olita.tech.silingan.dto.res.PlatformGrowthPointResponse;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.InvitationStatus;
import com.ria.olita.tech.silingan.entity.InvitationType;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.UserStatus;
import com.ria.olita.tech.silingan.repository.AttentionCommunityRow;
import com.ria.olita.tech.silingan.repository.CommunityMonthlyCount;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.CommunityStatusCount;
import com.ria.olita.tech.silingan.repository.InvitationRepository;
import com.ria.olita.tech.silingan.repository.PlatformActivityRow;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.service.PlatformDashboardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Platform-wide, read-only aggregation over existing records. Callers must already be authorized as
 * Platform Admin (enforced on the controller).
 *
 * <p>Domain rules applied:
 * <ul>
 *   <li>Active administrator: a COMMUNITY_ADMIN membership of an ACTIVE user (the rule
 *       {@code hasRoleInCommunity} already uses). Residents and administrators are counted as distinct
 *       people, not memberships.</li>
 *   <li>Community groups are mutually exclusive: ARCHIVE is inactive; otherwise no active administrator
 *       is "needs admin"; otherwise ACTIVE is active. A DRAFT community that already has an
 *       administrator is awaiting activation and is grouped as inactive.</li>
 *   <li>Pending invitation: ADMIN type, PENDING and not past {@code expiresAt}, evaluated against the
 *       request instant even if the expiry job has not run yet.</li>
 *   <li>Only pending invitations can be compared with the previous period: invited, accepted,
 *       revoked and expiry timestamps are retained. Communities, residents and administrators have no
 *       removal or deactivation history, so a past total cannot be reconstructed reliably.</li>
 *   <li>Recent activity has no audit source, so it is derived from retained timestamps of
 *       communities and ADMIN invitations (ids are {@code TYPE:sourceId}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PlatformDashboardServiceImpl implements PlatformDashboardService {

	static final int RECENT_ACTIVITY_LIMIT = 5;
	static final int ATTENTION_LIMIT = 10;

	private final CommunityRepository communityRepository;
	private final UserCommunityRepository userCommunityRepository;
	private final InvitationRepository invitationRepository;
	private final Clock clock;

	@Override
	public PlatformDashboardResponse getDashboard(PlatformGrowthPeriod growthPeriod) {
		log.debug("Platform dashboard requested: growthPeriod={}", growthPeriod.getValue());

		// One reference instant for every boundary. Entity timestamps are zone-less: communities use the
		// clock zone, invitations are written in UTC.
		ZonedDateTime now = ZonedDateTime.now(clock);
		ZonedDateTime currentStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.getZone());
		ZonedDateTime previousStart = currentStart.minusMonths(1);
		ZonedDateTime previousPoint = previousStart.plus(Duration.between(currentStart, now));
		if (previousPoint.isAfter(currentStart)) {
			previousPoint = currentStart;
		}

		var nowUtc = now.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
		var previousPointUtc = previousPoint.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

		List<CommunityStatusCount> statusCounts = communityRepository.countByStatusWithAdmin();
		PlatformCommunityStatusSummaryResponse byStatus = summarize(statusCounts);

		long pendingNow = invitationRepository.countPendingByType(InvitationType.ADMIN, InvitationStatus.PENDING, nowUtc);
		long pendingPrevious = invitationRepository.countPendingAt(InvitationType.ADMIN, previousPointUtc);

		PlatformDashboardMetricsResponse metrics = new PlatformDashboardMetricsResponse(
			DashboardMetricResponse.unavailable(byStatus.total()),
			DashboardMetricResponse.unavailable(userCommunityRepository
				.countDistinctUsersByRoleAndUserStatus(SilinganRealmRole.RESIDENT, UserStatus.ACTIVE)),
			DashboardMetricResponse.unavailable(userCommunityRepository
				.countDistinctUsersByRoleAndUserStatus(SilinganRealmRole.COMMUNITY_ADMIN, UserStatus.ACTIVE)),
			CommunityDashboardServiceImpl.withComparison(pendingNow, pendingPrevious)
		);

		return new PlatformDashboardResponse(
			metrics,
			communityGrowth(growthPeriod, currentStart),
			byStatus,
			recentActivities(now.getZone()),
			communitiesNeedingAttention(nowUtc),
			permissions()
		);
	}

	static PlatformCommunityStatusSummaryResponse summarize(List<CommunityStatusCount> counts) {
		long total = 0;
		long active = 0;
		long needsAdmin = 0;
		long inactive = 0;
		for (CommunityStatusCount row : counts) {
			long withoutAdmin = row.total() - row.withAdmin();
			total += row.total();
			if (row.status() == CommunityStatus.ARCHIVE) {
				inactive += row.total();
			} else {
				needsAdmin += withoutAdmin;
				if (row.status() == CommunityStatus.ACTIVE) {
					active += row.withAdmin();
				} else {
					inactive += row.withAdmin();
				}
			}
		}
		return new PlatformCommunityStatusSummaryResponse(total, active, needsAdmin, inactive);
	}

	/** Monthly new communities, including the current month; the bounded gap-filling happens here. */
	private List<PlatformGrowthPointResponse> communityGrowth(PlatformGrowthPeriod period, ZonedDateTime currentStart) {
		ZonedDateTime first = currentStart.minusMonths(period.getMonths() - 1L);
		ZonedDateTime end = currentStart.plusMonths(1);

		Map<YearMonth, Long> counts = new HashMap<>();
		for (CommunityMonthlyCount row : communityRepository
			.countCreatedPerMonth(first.toLocalDateTime(), end.toLocalDateTime())) {
			counts.put(YearMonth.of(row.year(), row.month()), row.count());
		}

		YearMonth start = YearMonth.from(first);
		List<PlatformGrowthPointResponse> points = new ArrayList<>(period.getMonths());
		for (int i = 0; i < period.getMonths(); i++) {
			YearMonth month = start.plusMonths(i);
			points.add(new PlatformGrowthPointResponse(month.toString(), counts.getOrDefault(month, 0L)));
		}
		return points;
	}

	/** Each source is limited to its newest N rows, so the merged top N is exact. */
	private List<PlatformActivityResponse> recentActivities(ZoneId zone) {
		Pageable limit = PageRequest.of(0, RECENT_ACTIVITY_LIMIT);
		List<PlatformActivityResponse> all = new ArrayList<>();
		addActivities(all, PlatformActivityType.COMMUNITY_CREATED, communityRepository.findRecentlyCreated(limit), zone);
		addActivities(all, PlatformActivityType.COMMUNITY_ADMIN_INVITED,
			invitationRepository.findRecentInvited(InvitationType.ADMIN, limit), ZoneOffset.UTC);
		addActivities(all, PlatformActivityType.INVITATION_ACCEPTED,
			invitationRepository.findRecentAccepted(InvitationType.ADMIN, limit), ZoneOffset.UTC);
		addActivities(all, PlatformActivityType.INVITATION_REVOKED,
			invitationRepository.findRecentRevoked(InvitationType.ADMIN, limit), ZoneOffset.UTC);

		return all.stream()
			.sorted(Comparator.comparing(PlatformActivityResponse::occurredAt)
				.thenComparing(PlatformActivityResponse::id)
				.reversed())
			.limit(RECENT_ACTIVITY_LIMIT)
			.toList();
	}

	private void addActivities(List<PlatformActivityResponse> target, PlatformActivityType type,
		List<PlatformActivityRow> rows, ZoneId zone) {
		for (PlatformActivityRow row : rows) {
			target.add(new PlatformActivityResponse(
				type.name() + ":" + row.sourceId(),
				type,
				type.getTitle(),
				row.communityName(),
				row.occurredAt().atZone(zone).toInstant(),
				row.communityId()
			));
		}
	}

	private List<PlatformAttentionCommunityResponse> communitiesNeedingAttention(java.time.LocalDateTime nowUtc) {
		return communityRepository.findNeedingAttention(nowUtc, PageRequest.of(0, ATTENTION_LIMIT)).stream()
			.map(this::toAttention)
			.toList();
	}

	/** Priority: NO_ADMIN, INACTIVE_COMMUNITY, ADMIN_INVITATION_PENDING. */
	PlatformAttentionCommunityResponse toAttention(AttentionCommunityRow row) {
		List<PlatformAttentionIssue> issues = new ArrayList<>();
		if (row.adminCount() == 0) {
			issues.add(PlatformAttentionIssue.NO_ADMIN);
		}
		if (row.status() != CommunityStatus.ACTIVE) {
			issues.add(PlatformAttentionIssue.INACTIVE_COMMUNITY);
		}
		if (row.pendingAdminInvitations() > 0) {
			issues.add(PlatformAttentionIssue.ADMIN_INVITATION_PENDING);
		}
		return new PlatformAttentionCommunityResponse(
			row.communityId(), row.name(), row.residentCount(), row.adminCount(),
			issues.isEmpty() ? null : issues.get(0), List.copyOf(issues));
	}

	/**
	 * Every platform-level action is gated by the Platform Admin role on its own endpoint
	 * (community create/list, admin invitations, invitation listing); there is no finer-grained
	 * permission, and no platform report-generation endpoint exists, so none is advertised.
	 */
	private PlatformDashboardPermissionsResponse permissions() {
		boolean platformAdmin = UserContextHolder.isPlatformAdmin();
		return new PlatformDashboardPermissionsResponse(platformAdmin, platformAdmin, platformAdmin, platformAdmin);
	}
}
