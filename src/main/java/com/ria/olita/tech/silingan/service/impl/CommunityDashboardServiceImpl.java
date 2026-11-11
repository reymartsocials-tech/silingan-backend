package com.ria.olita.tech.silingan.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.res.CommunityDashboardResponse;
import com.ria.olita.tech.silingan.dto.res.DashboardAnnouncementResponse;
import com.ria.olita.tech.silingan.dto.res.DashboardContactResponse;
import com.ria.olita.tech.silingan.dto.res.DashboardMetricResponse;
import com.ria.olita.tech.silingan.dto.res.DashboardMetricsResponse;
import com.ria.olita.tech.silingan.dto.res.DashboardPermissionsResponse;
import com.ria.olita.tech.silingan.dto.res.DashboardReportResponse;
import com.ria.olita.tech.silingan.dto.res.DashboardReportStatusResponse;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.IssueStatus;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.UserStatus;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.AnnouncementRepository;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.DirectoryContactRepository;
import com.ria.olita.tech.silingan.repository.IssueRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.spec.DirectoryContactSpecifications;
import com.ria.olita.tech.silingan.service.CommunityDashboardService;
import com.ria.olita.tech.silingan.service.PermissionService;

import lombok.RequiredArgsConstructor;

/**
 * Aggregates existing data for the community dashboard; nothing is persisted. Every query is scoped
 * by the community and a section is only queried when the caller may view its module.
 *
 * <p>Only the announcement metric carries a month-over-month comparison. Resident, staff and report
 * state is stored as a current value only (no status history), so a previous-period value cannot be
 * reconstructed reliably and those comparisons are reported as unavailable.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityDashboardServiceImpl implements CommunityDashboardService {

	static final int RECENT_ANNOUNCEMENT_LIMIT = 5;
	static final int RECENT_REPORT_LIMIT = 5;
	static final int DIRECTORY_CONTACT_LIMIT = 5;

	private static final EnumSet<SilinganRealmRole> STAFF_ROLES =
		EnumSet.of(SilinganRealmRole.STAFF, SilinganRealmRole.COMMUNITY_ADMIN);

	private final CommunityRepository communityRepository;
	private final UserCommunityRepository userCommunityRepository;
	private final IssueRepository issueRepository;
	private final AnnouncementRepository announcementRepository;
	private final DirectoryContactRepository directoryContactRepository;
	private final PermissionService permissionService;
	private final Clock clock;

	@Override
	public CommunityDashboardResponse getDashboard(UUID communityId) {
		if (communityId == null || !communityRepository.existsByIdAndStatus(communityId, CommunityStatus.ACTIVE)) {
			throw new NotFoundException("Community not found");
		}

		// Boundaries are fixed once so every period-based figure uses the same instant.
		ZonedDateTime now = ZonedDateTime.now(clock);

		boolean canViewResidents = permissionService.canView(Domain.RESIDENT, communityId);
		boolean canViewStaff = permissionService.canView(Domain.STAFF, communityId);
		boolean canViewAnnouncements = permissionService.canView(Domain.ANNOUNCEMENT, communityId);
		boolean canViewReports = permissionService.canView(Domain.REPORT, communityId);
		boolean canViewDirectory = permissionService.canView(Domain.DIRECTORY, communityId);

		List<DashboardReportStatusResponse> reportStatus = canViewReports
			? reportStatusDistribution(communityId)
			: List.of();

		DashboardMetricsResponse metrics = new DashboardMetricsResponse(
			canViewResidents ? DashboardMetricResponse.unavailable(
				userCommunityRepository.countByCommunityIdAndRoleAndUserStatus(
					communityId, SilinganRealmRole.RESIDENT, UserStatus.ACTIVE)) : null,
			canViewReports ? DashboardMetricResponse.unavailable(openReports(reportStatus)) : null,
			canViewAnnouncements ? announcementsPublishedThisMonth(communityId, now) : null,
			canViewStaff ? DashboardMetricResponse.unavailable(
				userCommunityRepository.countByCommunityIdAndRoleInAndUserStatus(
					communityId, STAFF_ROLES, UserStatus.ACTIVE)) : null
		);

		List<DashboardAnnouncementResponse> recentAnnouncements = canViewAnnouncements
			? announcementRepository.findRecentPublishedForDashboard(
				communityId, PageRequest.of(0, RECENT_ANNOUNCEMENT_LIMIT))
			: List.of();

		List<DashboardReportResponse> recentReports = canViewReports
			? issueRepository.findRecentForDashboard(communityId, PageRequest.of(0, RECENT_REPORT_LIMIT))
			: List.of();

		List<DashboardContactResponse> contacts = canViewDirectory ? directoryContacts(communityId) : List.of();

		return new CommunityDashboardResponse(
			metrics,
			recentAnnouncements,
			reportStatus,
			recentReports,
			contacts,
			permissions(communityId)
		);
	}

	/** Stable chart structure: every status is present, with zero when no report has it. */
	private List<DashboardReportStatusResponse> reportStatusDistribution(UUID communityId) {
		Map<IssueStatus, Long> counts = new EnumMap<>(IssueStatus.class);
		for (DashboardReportStatusResponse row : issueRepository.countByStatusForCommunity(communityId)) {
			counts.merge(row.status(), row.count(), Long::sum);
		}
		return Arrays.stream(IssueStatus.values())
			.map(status -> new DashboardReportStatusResponse(status, counts.getOrDefault(status, 0L)))
			.toList();
	}

	private long openReports(List<DashboardReportStatusResponse> distribution) {
		return distribution.stream()
			.filter(row -> row.status().isOpen())
			.mapToLong(DashboardReportStatusResponse::count)
			.sum();
	}

	/**
	 * Month-to-date versus the same elapsed time of the previous month. The previous window is capped
	 * at the start of the current month so a longer current month never reaches into itself.
	 */
	private DashboardMetricResponse announcementsPublishedThisMonth(UUID communityId, ZonedDateTime now) {
		ZonedDateTime currentStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.getZone());
		ZonedDateTime previousStart = currentStart.minusMonths(1);
		ZonedDateTime previousEnd = previousStart.plus(Duration.between(currentStart, now));
		if (previousEnd.isAfter(currentStart)) {
			previousEnd = currentStart;
		}

		long current = announcementRepository.countPublishedBetween(
			communityId, currentStart.toLocalDateTime(), now.toLocalDateTime());
		long previous = announcementRepository.countPublishedBetween(
			communityId, previousStart.toLocalDateTime(), previousEnd.toLocalDateTime());

		return withComparison(current, previous);
	}

	public static DashboardMetricResponse withComparison(long current, long previous) {
		if (previous <= 0) {
			return DashboardMetricResponse.unavailable(current);
		}
		double change = BigDecimal.valueOf(current - previous)
			.multiply(BigDecimal.valueOf(100))
			.divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP)
			.doubleValue();
		return new DashboardMetricResponse(current, change, true);
	}

	private List<DashboardContactResponse> directoryContacts(UUID communityId) {
		// Same active-only ordering as the resident directory; the limit is applied in the query.
		return directoryContactRepository
			.findBy(DirectoryContactSpecifications.forResident(communityId),
				query -> query.limit(DIRECTORY_CONTACT_LIMIT).all())
			.stream()
			.map(contact -> new DashboardContactResponse(
				contact.getId(),
				contact.getDisplayName(),
				contact.getCategory(),
				contact.getContactType(),
				contact.getPrimaryPhoneNumber(),
				contact.getOperatingHours(),
				contact.getCategory().isEmergency()))
			.toList();
	}

	private DashboardPermissionsResponse permissions(UUID communityId) {
		return new DashboardPermissionsResponse(
			permissionService.canManage(Domain.RESIDENT, communityId),
			permissionService.canManage(Domain.STAFF, communityId),
			permissionService.canManage(Domain.ANNOUNCEMENT, communityId),
			permissionService.canView(Domain.REPORT, communityId),
			permissionService.canManage(Domain.REPORT, communityId),
			permissionService.canManage(Domain.DIRECTORY, communityId)
		);
	}
}
