package com.ria.olita.tech.silingan.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.req.UpsertIssueRoutingRequest;
import com.ria.olita.tech.silingan.dto.res.IssueRoutingResponse;
import com.ria.olita.tech.silingan.dto.res.IssueRoutingStatus;
import com.ria.olita.tech.silingan.dto.res.RoutingStaffSummaryResponse;
import com.ria.olita.tech.silingan.dto.res.StaffRoutingDependenciesResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityIssueRouting;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.IssueCategory;
import com.ria.olita.tech.silingan.entity.IssueRoutingDestination;
import com.ria.olita.tech.silingan.entity.IssueStatus;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.CommunityIssueRoutingRepository;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.IssueCategoryRepository;
import com.ria.olita.tech.silingan.repository.IssueRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.service.IssueRoutingService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class IssueRoutingServiceImpl implements IssueRoutingService {

	private static final Set<SilinganRealmRole> STAFF_ROLES = Set.of(SilinganRealmRole.STAFF, SilinganRealmRole.COMMUNITY_ADMIN);
	private static final Set<IssueStatus> UNRESOLVED_STATUSES = Set.of(
		IssueStatus.OPEN,
		IssueStatus.IN_PROGRESS,
		IssueStatus.AWAITING_RESIDENT_CONFIRMATION
	);

	private final CommunityIssueRoutingRepository routingRepository;
	private final CommunityRepository communityRepository;
	private final IssueCategoryRepository issueCategoryRepository;
	private final IssueRepository issueRepository;
	private final UserCommunityRepository userCommunityRepository;
	private final StaffRoutingEligibilityChecker eligibilityChecker;

	@Override
	@Transactional(readOnly = true)
	public List<IssueRoutingResponse> getRoutingConfiguration(UUID communityId) {
		requireCommunity(communityId);
		Map<UUID, CommunityIssueRouting> routesByCategory = routingRepository.findByCommunityIdWithStaff(communityId)
			.stream()
			.collect(Collectors.toMap(CommunityIssueRouting::getCategoryId, Function.identity(), (first, second) -> first));

		return issueCategoryRepository.findByCommunityId(communityId).stream()
			.sorted(Comparator.comparing(IssueCategory::getDisplayOrder, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(IssueCategory::getName, Comparator.nullsLast(Comparator.naturalOrder())))
			.map(category -> toResponse(communityId, category, routesByCategory.get(category.getId())))
			.toList();
	}

	@Override
	public IssueRoutingResponse upsertRouting(UUID communityId, UUID categoryId, UpsertIssueRoutingRequest request) {
		Community community = requireActiveCommunity(communityId);
		IssueCategory category = issueCategoryRepository.findById(categoryId)
			.filter(candidate -> communityId.equals(candidate.getCommunityId()))
			.orElseThrow(() -> new NotFoundException("Issue category not found"));

		StaffRoutingEligibilityChecker.validateDestination(request.destinationType(), request.staffMembershipId());
		UserCommunity membership = request.destinationType() == IssueRoutingDestination.STAFF
			? eligibilityChecker.requireEligible(communityId, request.staffMembershipId())
			: null;

		// Existing reports keep their assignment; only newly submitted reports use the updated route.
		CommunityIssueRouting routing = routingRepository.findByCommunityIdAndCategoryId(communityId, categoryId)
			.orElseGet(() -> CommunityIssueRouting.builder()
				.community(community)
				.communityId(communityId)
				.category(category)
				.categoryId(categoryId)
				.build());
		routing.setDestinationType(request.destinationType());
		routing.setAssignedStaffMembership(membership);
		routing.setAssignedStaffMembershipId(membership == null ? null : membership.getId());
		routing.setActive(true);

		CommunityIssueRouting saved = routingRepository.saveAndFlush(routing);
		return toResponse(communityId, category, saved);
	}

	@Override
	@Transactional(readOnly = true)
	public List<RoutingStaffSummaryResponse> getEligibleStaff(UUID communityId, UUID categoryId) {
		requireCommunity(communityId);
		if (categoryId != null) {
			issueCategoryRepository.findById(categoryId)
				.filter(candidate -> communityId.equals(candidate.getCommunityId()))
				.orElseThrow(() -> new NotFoundException("Issue category not found"));
		}
		return userCommunityRepository.findStaffByCommunityWithFilters(communityId, STAFF_ROLES, null).stream()
			.filter(membership -> eligibilityChecker.isEligible(membership, communityId))
			.map(membership -> toStaffSummary(membership, true))
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public StaffRoutingDependenciesResponse getStaffRoutingDependencies(UUID communityId, UUID staffUserId) {
		requireCommunity(communityId);
		UserCommunity membership = userCommunityRepository.findByUserIdAndCommunityId(staffUserId, communityId)
			.filter(candidate -> STAFF_ROLES.contains(candidate.getRole()))
			.orElseThrow(() -> new NotFoundException("Staff member not found in this community"));

		List<StaffRoutingDependenciesResponse.RoutedCategoryResponse> routedCategories = routingRepository
			.findActiveByCommunityIdAndStaffMembershipId(communityId, membership.getId())
			.stream()
			.map(route -> new StaffRoutingDependenciesResponse.RoutedCategoryResponse(
				route.getCategoryId(), route.getCategory().getName()))
			.toList();
		long unresolved = issueRepository.countByAssignedStaffMembershipIdAndStatusIn(membership.getId(), UNRESOLVED_STATUSES);

		return new StaffRoutingDependenciesResponse(
			staffUserId,
			membership.getId(),
			routedCategories,
			unresolved,
			routedCategories.isEmpty()
		);
	}

	private IssueRoutingResponse toResponse(UUID communityId, IssueCategory category, CommunityIssueRouting routing) {
		boolean categoryActive = Boolean.TRUE.equals(category.getIsActive());
		if (routing == null) {
			return new IssueRoutingResponse(category.getId(), category.getName(), categoryActive,
				IssueRoutingDestination.PMO_REVIEW_QUEUE, null, IssueRoutingStatus.NOT_CONFIGURED, null);
		}

		RoutingStaffSummaryResponse staff = null;
		IssueRoutingStatus status = Boolean.TRUE.equals(routing.getActive()) ? IssueRoutingStatus.ACTIVE : IssueRoutingStatus.INVALID;
		if (routing.getDestinationType() == IssueRoutingDestination.STAFF) {
			UserCommunity membership = routing.getAssignedStaffMembership();
			boolean eligible = eligibilityChecker.isEligible(membership, communityId);
			staff = membership == null ? null : toStaffSummary(membership, eligible);
			if (!eligible) {
				status = IssueRoutingStatus.INVALID;
			}
		}
		return new IssueRoutingResponse(category.getId(), category.getName(), categoryActive,
			routing.getDestinationType(), staff, status, routing.getUpdatedAt());
	}

	private RoutingStaffSummaryResponse toStaffSummary(UserCommunity membership, boolean eligible) {
		return new RoutingStaffSummaryResponse(
			membership.getId(),
			StaffRoutingEligibilityChecker.displayName(membership.getUser()),
			StaffRoutingEligibilityChecker.designation(membership),
			eligible
		);
	}

	private void requireCommunity(UUID communityId) {
		if (!communityRepository.existsById(communityId)) {
			throw new NotFoundException("Community not found");
		}
	}

	private Community requireActiveCommunity(UUID communityId) {
		return communityRepository.findByIdAndStatus(communityId, CommunityStatus.ACTIVE)
			.orElseThrow(() -> new NotFoundException("Community not found"));
	}
}
