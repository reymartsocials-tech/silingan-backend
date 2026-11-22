package com.ria.olita.tech.silingan.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.req.CreateNotificationCommand;
import com.ria.olita.tech.silingan.dto.res.NotificationResponse;
import com.ria.olita.tech.silingan.entity.CommunityIssueRouting;
import com.ria.olita.tech.silingan.entity.Issue;
import com.ria.olita.tech.silingan.entity.IssueAssignmentHistory;
import com.ria.olita.tech.silingan.entity.IssueAssignmentSource;
import com.ria.olita.tech.silingan.entity.IssueAssignmentStatus;
import com.ria.olita.tech.silingan.entity.IssueRoutingDestination;
import com.ria.olita.tech.silingan.entity.NotificationCategory;
import com.ria.olita.tech.silingan.entity.NotificationPriority;
import com.ria.olita.tech.silingan.entity.NotificationReferenceType;
import com.ria.olita.tech.silingan.entity.NotificationType;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.event.ReportAssignmentNotificationEvent;
import com.ria.olita.tech.silingan.exception.ConflictException;
import com.ria.olita.tech.silingan.repository.CommunityIssueRoutingRepository;
import com.ria.olita.tech.silingan.repository.IssueAssignmentHistoryRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.service.NotificationService;
import com.ria.olita.tech.silingan.service.ReportAssignmentService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ReportAssignmentServiceImpl implements ReportAssignmentService {

	private final CommunityIssueRoutingRepository routingRepository;
	private final IssueAssignmentHistoryRepository historyRepository;
	private final UserCommunityRepository userCommunityRepository;
	private final StaffRoutingEligibilityChecker eligibilityChecker;
	private final NotificationService notificationService;
	private final ApplicationEventPublisher eventPublisher;

	@Override
	public void assignOnSubmission(Issue issue) {
		UUID communityId = communityIdOf(issue);
		Optional<UserCommunity> routedStaff = routingRepository
			.findByCommunityIdAndCategoryId(communityId, issue.getCategory().getId())
			.filter(route -> Boolean.TRUE.equals(route.getActive()))
			.filter(route -> route.getDestinationType() == IssueRoutingDestination.STAFF)
			.map(CommunityIssueRouting::getAssignedStaffMembership)
			.filter(membership -> eligibilityChecker.isEligible(membership, communityId));

		if (routedStaff.isPresent()) {
			UserCommunity membership = routedStaff.get();
			String designation = StaffRoutingEligibilityChecker.designation(membership);
			applyAssignment(issue, IssueRoutingDestination.STAFF, membership, IssueAssignmentSource.CATEGORY_ROUTING,
				null, null, "Automatically assigned",
				"Your report was assigned to " + designation + " based on the selected "
					+ issue.getCategory().getName() + " category.");
		} else {
			applyAssignment(issue, IssueRoutingDestination.PMO_REVIEW_QUEUE, null, IssueAssignmentSource.PMO_FALLBACK,
				null, null, "Sent for PMO review",
				"Your report was received and is waiting for staff assignment.");
		}
	}

	@Override
	public void reassign(Issue issue, IssueRoutingDestination destination, UUID staffMembershipId, String reason, User actor) {
		UUID communityId = communityIdOf(issue);
		StaffRoutingEligibilityChecker.validateDestination(destination, staffMembershipId);

		UserCommunity membership = destination == IssueRoutingDestination.STAFF
			? eligibilityChecker.requireEligible(communityId, staffMembershipId)
			: null;

		UUID currentMembershipId = issue.getAssignedStaffMembership() == null ? null : issue.getAssignedStaffMembership().getId();
		UUID newMembershipId = membership == null ? null : membership.getId();
		if (issue.getAssignmentDestination() == destination && Objects.equals(currentMembershipId, newMembershipId)) {
			throw new ConflictException("Report is already assigned to this destination");
		}

		IssueAssignmentSource source = issue.getAssignmentDestination() == IssueRoutingDestination.STAFF
			? IssueAssignmentSource.MANUAL_REASSIGNMENT
			: IssueAssignmentSource.MANUAL_ASSIGNMENT;
		String message = membership == null
			? "Your report is waiting for staff assignment."
			: "Your report is now being handled by " + StaffRoutingEligibilityChecker.designation(membership) + ".";
		applyAssignment(issue, destination, membership, source, reason.trim(), actor, "Report reassigned", message);
	}

	private void applyAssignment(
		Issue issue,
		IssueRoutingDestination destination,
		UserCommunity membership,
		IssueAssignmentSource source,
		String reason,
		User actor,
		String activityTitle,
		String activityMessage
	) {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		IssueRoutingDestination previousDestination = issue.getAssignmentDestination();
		UserCommunity previousMembership = issue.getAssignedStaffMembership();

		issue.setAssignmentDestination(destination);
		issue.setAssignedStaffMembership(membership);
		issue.setAssignedTo(membership == null ? null : membership.getUser());
		issue.setAssignmentSource(source);
		issue.setAssignmentStatus(destination == IssueRoutingDestination.STAFF
			? IssueAssignmentStatus.ASSIGNED
			: IssueAssignmentStatus.AWAITING_TRIAGE);
		issue.setAssignedAt(now);

		IssueAssignmentHistory history = historyRepository.save(IssueAssignmentHistory.builder()
			.issue(issue)
			.issueId(issue.getId())
			.previousDestination(previousDestination)
			.previousStaffMembership(previousMembership)
			.newDestination(destination)
			.newStaffMembership(membership)
			.assignmentSource(source)
			.reason(reason)
			.changedBy(actor)
			.changedAt(now)
			.activityTitle(activityTitle)
			.activityMessage(activityMessage)
			.build());

		notifyHandlers(issue, destination, membership, history.getId());
	}

	private void notifyHandlers(Issue issue, IssueRoutingDestination destination, UserCommunity membership, UUID historyId) {
		UUID communityId = communityIdOf(issue);
		Set<UUID> recipients = new LinkedHashSet<>();
		NotificationType type;
		String title;
		String body;
		if (destination == IssueRoutingDestination.STAFF) {
			recipients.add(membership.getUserId() != null ? membership.getUserId() : membership.getUser().getId());
			type = NotificationType.REPORT_ASSIGNED;
			title = "New report assigned";
			body = "A " + issue.getCategory().getName() + " report has been assigned to you.";
		} else {
			recipients.addAll(userCommunityRepository.findActiveTriageUserIdsByCommunityId(communityId));
			type = NotificationType.REPORT_SUBMITTED;
			title = "Report awaiting triage";
			body = "A " + issue.getCategory().getName() + " report is waiting for staff assignment.";
		}

		// sourceEventId is unique per history entry, so repeated processing never duplicates a notification.
		String sourceEventId = "report-assignment:" + historyId;
		List<UUID> orderedRecipients = List.copyOf(recipients);
		for (UUID recipientUserId : orderedRecipients) {
			NotificationResponse notification = notificationService.createNotification(CreateNotificationCommand.builder()
				.recipientUserId(recipientUserId)
				.communityId(communityId)
				.type(type)
				.category(NotificationCategory.REPORT)
				.priority(NotificationPriority.NORMAL)
				.title(title)
				.body(body)
				.referenceType(NotificationReferenceType.REPORT)
				.referenceId(issue.getId().toString())
				.actionPath("/reports/" + issue.getId())
				.sourceEventId(sourceEventId)
				.build());
			eventPublisher.publishEvent(new ReportAssignmentNotificationEvent(
				notification.id(),
				type,
				issue.getId(),
				communityId,
				recipientUserId,
				ReportAssignmentNotificationEvent.ROUTE_REPORT_DETAILS
			));
		}
	}

	private UUID communityIdOf(Issue issue) {
		return issue.getCommunityId() != null ? issue.getCommunityId() : issue.getCommunity().getId();
	}
}
