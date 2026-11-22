package com.ria.olita.tech.silingan.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.res.AttachmentResponse;
import com.ria.olita.tech.silingan.dto.res.ReportAssignmentActivityResponse;
import com.ria.olita.tech.silingan.dto.res.ReportResponse;
import com.ria.olita.tech.silingan.dto.req.CreateReportRequest;
import com.ria.olita.tech.silingan.dto.req.ReassignReportRequest;
import com.ria.olita.tech.silingan.dto.req.ReportFilterCriteria;
import com.ria.olita.tech.silingan.dto.req.ReportResolutionDecisionRequest;
import com.ria.olita.tech.silingan.dto.req.UpdateReportRequest;
import com.ria.olita.tech.silingan.entity.AttachmentOwnerType;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.Issue;
import com.ria.olita.tech.silingan.entity.IssueResolutionDecision;
import com.ria.olita.tech.silingan.entity.IssueCategory;
import com.ria.olita.tech.silingan.entity.IssueRoutingDestination;
import com.ria.olita.tech.silingan.entity.IssueStatus;
import com.ria.olita.tech.silingan.entity.ResolutionDecision;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.exception.ConflictException;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.repository.AttachmentReferenceRepository;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.IssueAssignmentHistoryRepository;
import com.ria.olita.tech.silingan.repository.IssueCategoryRepository;
import com.ria.olita.tech.silingan.repository.IssueRepository;
import com.ria.olita.tech.silingan.repository.IssueResolutionDecisionRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.repository.spec.IssueSpecifications;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.service.ReportAssignmentService;
import com.ria.olita.tech.silingan.service.ReportService;
import com.ria.olita.tech.silingan.service.impl.ReportResolutionNotificationService;

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
	private final AttachmentReferenceRepository attachmentReferenceRepository;
	private final ReportAssignmentService reportAssignmentService;
	private final IssueAssignmentHistoryRepository assignmentHistoryRepository;
	private final IssueResolutionDecisionRepository resolutionDecisionRepository;
	private final ReportResolutionNotificationService resolutionNotificationService;

	private static final String UPDATE_ASSIGNMENT_REASON = "Assigned through report update";

	@Override
	public ReportResponse submitReport(CreateReportRequest request) {
		UserContext context = requireContext();
		UUID currentUserId = parseCurrentUserId(context);
		User currentUser = userRepository.findById(currentUserId)
			.orElseThrow(() -> new NotFoundException("User not found"));

		Community community = communityRepository.findByIdAndStatus(request.communityId(), CommunityStatus.ACTIVE)
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
			.urgencyLevel(request.urgencyLevel())
			.location(request.location())
			.latitude(request.latitude())
			.longitude(request.longitude())
			.build();

		// Assignment is always derived server-side from community + category routing, never from the request.
		Issue saved = issueRepository.save(issue);
		reportAssignmentService.assignOnSubmission(saved);
		return buildReportResponseWithAttachments(saved);
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

		return issues.map(this::buildReportResponseWithAttachments);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<ReportResponse> getReportsWithFilters(UUID communityId, ReportFilterCriteria criteria, Pageable pageable) {
		UserContext context = requireContext();
		UUID currentUserId = parseCurrentUserId(context);

		if (communityId == null && !UserContextHolder.isPlatformAdmin()) {
			throw new ValidationException("Community ID is required for non-admin users");
		}

		if (communityId != null) {
			communityRepository.findByIdAndStatus(communityId, CommunityStatus.ACTIVE)
				.orElseThrow(() -> new NotFoundException("Community not found"));
			ensureMembership(currentUserId, communityId);
		}

		Page<Issue> issues;
		if (criteria != null && Boolean.TRUE.equals(criteria.getAssignedToMe())) {
			if (communityId == null) {
				throw new ValidationException("Community ID is required when filtering reports assigned to me");
			}
			criteria.setAssignedToId(currentUserId);
			issues = issueRepository.findAll(
				IssueSpecifications.filterByCommunityAndCriteria(communityId, criteria),
				withDeterministicSort(pageable)
			);
		} else if (communityId != null && !canViewAllReports(context, communityId)) {
			issues = issueRepository.findAll(
				IssueSpecifications.filterByReporterAndCriteria(communityId, currentUserId, criteria),
				pageable
			);
		} else {
			issues = issueRepository.findAll(
				IssueSpecifications.filterByCommunityAndCriteria(communityId, criteria),
				pageable
			);
		}

		return issues.map(this::buildReportResponseWithAttachments);
	}

	@Override
	@Transactional(readOnly = true)
	public ReportResponse getReportDetails(UUID reportId) {
		UserContext context = requireContext();
		UUID currentUserId = parseCurrentUserId(context);

		Issue issue = issueRepository.findByIdWithDetails(reportId)
			.orElseThrow(() -> new NotFoundException("Report not found"));
		ensureMembership(currentUserId, issue.getCommunityId());

		if (!isOwner(issue, currentUserId) && !isAssignee(issue, currentUserId)
			&& !canViewAllReports(context, issue.getCommunityId())) {
			throw new ForbiddenException("You are not allowed to view this report");
		}

		List<ReportAssignmentActivityResponse> activities = assignmentHistoryRepository
			.findByIssueIdOrderByChangedAtAscIdAsc(issue.getId())
			.stream()
			.map(history -> new ReportAssignmentActivityResponse(
				history.getActivityTitle(), history.getActivityMessage(), history.getChangedAt()))
			.toList();
		return buildReportResponseWithAttachments(issue).withAssignmentActivities(activities);
	}

	@Override
	public ReportResponse updateReport(UUID reportId, UpdateReportRequest request) {
		UserContext context = requireContext();
		UUID currentUserId = parseCurrentUserId(context);

		Issue issue = issueRepository.findByIdForUpdate(reportId)
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
		if (request.urgencyLevel() != null) {
			issue.setUrgencyLevel(request.urgencyLevel());
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
		boolean resolutionAwaitingConfirmation = false;
		if (request.status() != null) {
			if (!canManageAll) {
				throw new ForbiddenException("Only staff/community administrators can change report status");
			}
			if (request.status() == IssueStatus.CLOSED) {
				throw new ForbiddenException("Only the reporting resident can confirm report closure");
			}
			if (request.status() == IssueStatus.AWAITING_RESIDENT_CONFIRMATION) {
				throw new ValidationException("Use RESOLVED to request resident confirmation");
			}
			if (issue.getStatus() == IssueStatus.AWAITING_RESIDENT_CONFIRMATION
				&& request.status() != IssueStatus.RESOLVED) {
				throw new ConflictException("Report is awaiting the reporting resident's resolution decision");
			}
			IssueStatus newStatus = request.status() == IssueStatus.RESOLVED
				? IssueStatus.AWAITING_RESIDENT_CONFIRMATION
				: request.status();
			if (isTerminal(issue.getStatus()) && issue.getStatus() != newStatus) {
				throw new ConflictException("A closed, resolved, or cancelled report cannot change status");
			}
			resolutionAwaitingConfirmation = newStatus == IssueStatus.AWAITING_RESIDENT_CONFIRMATION
				&& issue.getStatus() != IssueStatus.AWAITING_RESIDENT_CONFIRMATION;
			issue.setStatus(newStatus);
			if (newStatus == IssueStatus.AWAITING_RESIDENT_CONFIRMATION && issue.getResolvedAt() == null) {
				issue.setResolvedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
			} else if (newStatus == IssueStatus.OPEN || newStatus == IssueStatus.IN_PROGRESS) {
				issue.setResolvedAt(null);
			}
		}
		if (request.assignedToUserId() != null) {
			if (!canManageAll) {
				throw new ForbiddenException("Only staff/community administrators can assign reports");
			}
			boolean alreadyAssigned = issue.getAssignedTo() != null
				&& request.assignedToUserId().equals(issue.getAssignedTo().getId());
			if (!alreadyAssigned) {
				UserCommunity assigneeMembership = userCommunityRepository
					.findByUserIdAndCommunityId(request.assignedToUserId(), issue.getCommunityId())
					.orElseThrow(() -> new NotFoundException("Assigned user not found"));
				reportAssignmentService.reassign(issue, IssueRoutingDestination.STAFF, assigneeMembership.getId(),
					UPDATE_ASSIGNMENT_REASON, findCurrentUser(currentUserId));
			}
		}
		if (request.resolutionNotes() != null) {
			issue.setResolutionNotes(request.resolutionNotes());
		}

		Issue saved = issueRepository.save(issue);
		if (resolutionAwaitingConfirmation) {
			resolutionNotificationService.notifyResidentResolutionPending(saved, UUID.randomUUID());
		}
		return buildReportResponseWithAttachments(saved);
	}

	@Override
	public ReportResponse cancelReport(UUID reportId) {
		UserContext context = requireContext();
		UUID currentUserId = parseCurrentUserId(context);

		Issue issue = issueRepository.findByIdForUpdate(reportId)
			.orElseThrow(() -> new NotFoundException("Report not found"));
		ensureMembership(currentUserId, issue.getCommunityId());

		boolean owner = isOwner(issue, currentUserId);
		boolean canManageAll = canManageAllReports(context, issue.getCommunityId());
		if (!owner && !canManageAll) {
			throw new ForbiddenException("You are not allowed to cancel this report");
		}
		if (!owner && issue.getStatus() == IssueStatus.AWAITING_RESIDENT_CONFIRMATION) {
			throw new ConflictException("The reporting resident must decide whether the issue is resolved");
		}
		if (isTerminal(issue.getStatus())) {
			throw new ValidationException("Report is already resolved/closed/cancelled");
		}

		issue.setStatus(IssueStatus.CANCELLED);
		Issue saved = issueRepository.save(issue);
		return buildReportResponseWithAttachments(saved);
	}

	@Override
	public ReportResponse reassignReport(UUID reportId, ReassignReportRequest request) {
		UserContext context = requireContext();
		UUID currentUserId = parseCurrentUserId(context);

		// Row lock serialises concurrent reassignments of the same report.
		Issue issue = issueRepository.findByIdForUpdate(reportId)
			.orElseThrow(() -> new NotFoundException("Report not found"));
		ensureMembership(currentUserId, issue.getCommunityId());
		if (!canManageAllReports(context, issue.getCommunityId())) {
			throw new ForbiddenException("You are not allowed to reassign this report");
		}
		if (isTerminal(issue.getStatus())) {
			throw new ConflictException("Resolved, closed, or cancelled reports cannot be reassigned");
		}

		reportAssignmentService.reassign(issue, request.destinationType(), request.staffMembershipId(),
			request.reason(), findCurrentUser(currentUserId));
		Issue saved = issueRepository.save(issue);
		return buildReportResponseWithAttachments(saved);
	}

	@Override
	public ReportResponse respondToResolution(UUID reportId, ReportResolutionDecisionRequest request) {
		UserContext context = requireContext();
		UUID currentUserId = parseCurrentUserId(context);
		Issue issue = issueRepository.findByIdForUpdate(reportId)
			.orElseThrow(() -> new NotFoundException("Report not found"));
		ensureMembership(currentUserId, issue.getCommunityId());

		if (!isOwner(issue, currentUserId)) {
			throw new ForbiddenException("Only the reporting resident can decide whether the issue is resolved");
		}
		if (request == null || request.decision() == null) {
			throw new ValidationException("Resolution decision is required");
		}
		if (issue.getStatus() != IssueStatus.AWAITING_RESIDENT_CONFIRMATION) {
			throw new ConflictException("Report is not awaiting resident resolution confirmation");
		}

		ResolutionDecision decision = request.decision();
		boolean confirmedResolved = decision == ResolutionDecision.CONFIRMED_RESOLVED;
		issue.setStatus(confirmedResolved ? IssueStatus.CLOSED : IssueStatus.IN_PROGRESS);
		if (!confirmedResolved) {
			issue.setResolvedAt(null);
		}
		Issue saved = issueRepository.save(issue);

		User resident = findCurrentUser(currentUserId);
		IssueResolutionDecision decisionHistory = resolutionDecisionRepository.save(IssueResolutionDecision.builder()
			.issue(saved)
			.resident(resident)
			.decision(decision)
			.decidedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))
			.build());
		resolutionNotificationService.notifyHandlerDecision(saved, confirmedResolved, decisionHistory.getId());

		return buildReportResponseWithAttachments(saved);
	}

	private ReportResponse buildReportResponseWithAttachments(Issue issue) {
		ReportResponse response = ReportResponse.fromEntity(issue);
		
		List<AttachmentResponse> attachments = attachmentReferenceRepository
			.findByOwnerTypeAndOwnerId(AttachmentOwnerType.REPORT, issue.getId())
			.stream()
			.map(ref -> AttachmentResponse.builder()
				.id(ref.getAttachment().getId())
				.originalFileName(ref.getAttachment().getOriginalFileName())
				.fileName(ref.getAttachment().getFileName())
				.contentType(ref.getAttachment().getContentType())
				.fileSize(ref.getAttachment().getFileSize())
				.filePath(ref.getAttachment().getFilePath())
				.publicUrl(ref.getAttachment().getFilePath())
				.build())
			.collect(Collectors.toList());
		
		return response.withAttachments(attachments);
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

	private boolean isAssignee(Issue issue, UUID userId) {
		return issue.getAssignedTo() != null && issue.getAssignedTo().getId().equals(userId);
	}

	private User findCurrentUser(UUID currentUserId) {
		return userRepository.findById(currentUserId)
			.orElseThrow(() -> new NotFoundException("User not found"));
	}

	private Pageable withDeterministicSort(Pageable pageable) {
		if (pageable.isUnpaged() || pageable.getSort().isSorted()) {
			return pageable;
		}
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
			Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
	}

	private boolean isTerminal(IssueStatus status) {
		return status == IssueStatus.RESOLVED || status == IssueStatus.CLOSED || status == IssueStatus.CANCELLED;
	}
}
