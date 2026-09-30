package com.ria.olita.tech.silingan.service.impl;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.req.CreateReportRequest;
import com.ria.olita.tech.silingan.dto.req.UpdateReportRequest;
import com.ria.olita.tech.silingan.dto.res.ReportResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.Issue;
import com.ria.olita.tech.silingan.entity.IssueCategory;
import com.ria.olita.tech.silingan.entity.IssueStatus;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.IssueCategoryRepository;
import com.ria.olita.tech.silingan.repository.IssueRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.service.ReportService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

	private final IssueRepository issueRepository;
	private final IssueCategoryRepository issueCategoryRepository;
	private final CommunityRepository communityRepository;
	private final UserRepository userRepository;
	private final UserCommunityRepository userCommunityRepository;

	@Override
	public ReportResponse submitReport(CreateReportRequest request) {
		UserContext context = requireContext();
		UUID currentUserId = parseCurrentUserId(context);
		User currentUser = userRepository.findById(currentUserId)
			.orElseThrow(() -> new NotFoundException("User not found"));

		Community community = communityRepository.findById(request.communityId())
			.orElseThrow(() -> new NotFoundException("Community not found"));

		ensureMembership(currentUserId, request.communityId());

		IssueCategory category = issueCategoryRepository.findById(request.categoryId())
			.orElseThrow(() -> new NotFoundException("Issue category not found"));
		if (!category.getCommunityId().equals(request.communityId())) {
			throw new ValidationException("Issue category does not belong to the selected community");
		}
		if (!Boolean.TRUE.equals(category.getIsActive())) {
			throw new ValidationException("Issue category is not active");
		}

		Issue issue = Issue.builder()
			.community(community)
			.reporter(currentUser)
			.category(category)
			.title(request.title())
			.description(request.description())
			.status(IssueStatus.OPEN)
			.customCategory(request.customCategory())
			.imageUrl(request.imageUrl())
			.location(request.location())
			.latitude(request.latitude())
			.longitude(request.longitude())
			.build();

		return ReportResponse.fromEntity(issueRepository.save(issue));
	}

	@Override
	@Transactional(readOnly = true)
	public Page<ReportResponse> getReports(UUID communityId, IssueStatus status, Pageable pageable) {
		UserContext context = requireContext();
		UUID currentUserId = parseCurrentUserId(context);

		communityRepository.findById(communityId)
			.orElseThrow(() -> new NotFoundException("Community not found"));
		ensureMembership(currentUserId, communityId);

		Page<Issue> issues;
		if (canViewAllReports(context, communityId)) {
			issues = status == null
				? issueRepository.findByCommunityId(communityId, pageable)
				: issueRepository.findByCommunityIdAndStatus(communityId, status, pageable);
		} else {
			issues = issueRepository.findByReporterIdAndCommunityId(currentUserId, communityId, pageable);
		}

		return issues.map(ReportResponse::fromEntity);
	}

	@Override
	@Transactional(readOnly = true)
	public ReportResponse getReportDetails(UUID reportId) {
		UserContext context = requireContext();
		UUID currentUserId = parseCurrentUserId(context);

		Issue issue = issueRepository.findById(reportId)
			.orElseThrow(() -> new NotFoundException("Report not found"));
		ensureMembership(currentUserId, issue.getCommunityId());

		if (!isOwner(issue, currentUserId) && !canViewAllReports(context, issue.getCommunityId())) {
			throw new ForbiddenException("You are not allowed to view this report");
		}

		return ReportResponse.fromEntity(issue);
	}

	@Override
	public ReportResponse updateReport(UUID reportId, UpdateReportRequest request) {
		UserContext context = requireContext();
		UUID currentUserId = parseCurrentUserId(context);

		Issue issue = issueRepository.findById(reportId)
			.orElseThrow(() -> new NotFoundException("Report not found"));
		ensureMembership(currentUserId, issue.getCommunityId());

		boolean owner = isOwner(issue, currentUserId);
		boolean canManageAll = canManageAllReports(context, issue.getCommunityId());
		if (!owner && !canManageAll) {
			throw new ForbiddenException("You are not allowed to update this report");
		}

		if (owner && !canManageAll && isTerminal(issue.getStatus())) {
			throw new ValidationException("Resolved, closed, or cancelled reports cannot be updated");
		}

		if (request.title() != null && !request.title().isBlank()) {
			issue.setTitle(request.title());
		}
		if (request.description() != null && !request.description().isBlank()) {
			issue.setDescription(request.description());
		}
		if (request.customCategory() != null) {
			issue.setCustomCategory(request.customCategory());
		}
		if (request.imageUrl() != null) {
			issue.setImageUrl(request.imageUrl());
		}
		if (request.location() != null) {
			issue.setLocation(request.location());
		}
		if (request.latitude() != null) {
			issue.setLatitude(request.latitude());
		}
		if (request.longitude() != null) {
			issue.setLongitude(request.longitude());
		}
		if (request.categoryId() != null) {
			IssueCategory category = issueCategoryRepository.findById(request.categoryId())
				.orElseThrow(() -> new NotFoundException("Issue category not found"));
			if (!category.getCommunityId().equals(issue.getCommunityId())) {
				throw new ValidationException("Issue category does not belong to the report's community");
			}
			issue.setCategory(category);
		}
		if (request.status() != null) {
			if (!canManageAll) {
				throw new ForbiddenException("Only staff/community administrators can change report status");
			}
			issue.setStatus(request.status());
		}

		return ReportResponse.fromEntity(issueRepository.save(issue));
	}

	@Override
	public ReportResponse cancelReport(UUID reportId) {
		UserContext context = requireContext();
		UUID currentUserId = parseCurrentUserId(context);

		Issue issue = issueRepository.findById(reportId)
			.orElseThrow(() -> new NotFoundException("Report not found"));
		ensureMembership(currentUserId, issue.getCommunityId());

		boolean owner = isOwner(issue, currentUserId);
		boolean canManageAll = canManageAllReports(context, issue.getCommunityId());
		if (!owner && !canManageAll) {
			throw new ForbiddenException("You are not allowed to cancel this report");
		}
		if (isTerminal(issue.getStatus())) {
			throw new ValidationException("Report is already resolved/closed/cancelled");
		}

		issue.setStatus(IssueStatus.CANCELLED);
		return ReportResponse.fromEntity(issueRepository.save(issue));
	}

	private UserContext requireContext() {
		UserContext context = UserContextHolder.get();
		if (context == null || context.userId() == null) {
			throw new ForbiddenException("No authenticated user context");
		}
		return context;
	}

	private UUID parseCurrentUserId(UserContext context) {
		try {
			return UUID.fromString(context.userId());
		} catch (IllegalArgumentException ex) {
			throw new ForbiddenException("Invalid authenticated user context");
		}
	}

	private void ensureMembership(UUID userId, UUID communityId) {
		UserCommunity membership = userCommunityRepository.findByUserIdAndCommunityId(userId, communityId)
			.orElseThrow(() -> new ForbiddenException("User does not belong to this community"));
		if (membership.getRole() == null) {
			throw new ForbiddenException("User has no role in this community");
		}
	}

	private boolean canViewAllReports(UserContext context, UUID communityId) {
		return context.canView(Domain.REPORT, communityId);
	}

	private boolean canManageAllReports(UserContext context, UUID communityId) {
		return context.canManage(Domain.REPORT, communityId);
	}

	private boolean isOwner(Issue issue, UUID userId) {
		return issue.getReporter() != null && issue.getReporter().getId().equals(userId);
	}

	private boolean isTerminal(IssueStatus status) {
		return status == IssueStatus.RESOLVED || status == IssueStatus.CLOSED || status == IssueStatus.CANCELLED;
	}
}
