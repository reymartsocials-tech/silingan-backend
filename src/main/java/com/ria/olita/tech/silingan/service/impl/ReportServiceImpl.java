package com.ria.olita.tech.silingan.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.res.AttachmentResponse;
import com.ria.olita.tech.silingan.dto.res.ReportResponse;
import com.ria.olita.tech.silingan.dto.req.CreateReportRequest;
import com.ria.olita.tech.silingan.dto.req.ReportFilterCriteria;
import com.ria.olita.tech.silingan.dto.req.UpdateReportRequest;
import com.ria.olita.tech.silingan.entity.AttachmentOwnerType;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.Issue;
import com.ria.olita.tech.silingan.entity.IssueCategory;
import com.ria.olita.tech.silingan.entity.IssueStatus;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.repository.AttachmentReferenceRepository;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.IssueCategoryRepository;
import com.ria.olita.tech.silingan.repository.IssueRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.repository.spec.IssueSpecifications;
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
	private final AttachmentReferenceRepository attachmentReferenceRepository;

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

		Issue saved = issueRepository.save(issue);
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
		if (communityId != null && !canViewAllReports(context, communityId)) {
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

		if (!isOwner(issue, currentUserId) && !canViewAllReports(context, issue.getCommunityId())) {
			throw new ForbiddenException("You are not allowed to view this report");
		}

		return buildReportResponseWithAttachments(issue);
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
		if (request.status() != null) {
			if (!canManageAll) {
				throw new ForbiddenException("Only staff/community administrators can change report status");
			}
			issue.setStatus(request.status());
			if (isTerminal(request.status()) && issue.getResolvedAt() == null) {
				issue.setResolvedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
			}
		}
		if (request.assignedToUserId() != null) {
			if (!canManageAll) {
				throw new ForbiddenException("Only staff/community administrators can assign reports");
			}
			User assignedTo = userRepository.findById(request.assignedToUserId())
				.orElseThrow(() -> new NotFoundException("Assigned user not found"));
			ensureMembership(request.assignedToUserId(), issue.getCommunityId());
			issue.setAssignedTo(assignedTo);
		}
		if (request.resolutionNotes() != null) {
			issue.setResolutionNotes(request.resolutionNotes());
		}

		Issue saved = issueRepository.save(issue);
		return buildReportResponseWithAttachments(saved);
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
		Issue saved = issueRepository.save(issue);
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
		
		return new ReportResponse(
			response.id(),
			response.communityId(),
			response.reporterId(),
			response.reporterName(),
			response.assignedToId(),
			response.assignedToName(),
			response.categoryId(),
			response.categoryName(),
			response.title(),
			response.description(),
			response.status(),
			response.urgencyLevel(),
			response.customCategory(),
			response.location(),
			response.latitude(),
			response.longitude(),
			response.createdAt(),
			response.updatedAt(),
			response.resolvedAt(),
			response.resolutionNotes(),
			attachments
		);
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
