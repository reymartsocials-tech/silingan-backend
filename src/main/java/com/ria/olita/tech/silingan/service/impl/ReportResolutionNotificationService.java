package com.ria.olita.tech.silingan.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.req.CreateNotificationCommand;
import com.ria.olita.tech.silingan.dto.res.NotificationResponse;
import com.ria.olita.tech.silingan.entity.Issue;
import com.ria.olita.tech.silingan.entity.NotificationCategory;
import com.ria.olita.tech.silingan.entity.NotificationPriority;
import com.ria.olita.tech.silingan.entity.NotificationReferenceType;
import com.ria.olita.tech.silingan.entity.NotificationType;
import com.ria.olita.tech.silingan.event.ReportAssignmentNotificationEvent;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.service.NotificationService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class ReportResolutionNotificationService {

	private final NotificationService notificationService;
	private final UserCommunityRepository userCommunityRepository;
	private final ApplicationEventPublisher eventPublisher;

	public void notifyResidentResolutionPending(Issue issue, UUID sourceEventId) {
		createNotification(issue, issue.getReporter().getId(), NotificationType.REPORT_RESOLVED,
			"Please confirm the report resolution",
			"Staff marked your report as resolved. Please confirm whether the issue is fixed.",
			sourceEventId);
	}

	public void notifyHandlerDecision(Issue issue, boolean confirmedResolved, UUID sourceEventId) {
		List<UUID> recipients = issue.getAssignedTo() == null
			? userCommunityRepository.findActiveTriageUserIdsByCommunityId(issue.getCommunityId())
			: List.of(issue.getAssignedTo().getId());
		NotificationType type = confirmedResolved
			? NotificationType.REPORT_RESOLVED
			: NotificationType.REPORT_STATUS_CHANGED;
		String title = confirmedResolved ? "Report resolution confirmed" : "Report reopened by resident";
		String body = confirmedResolved
			? "The resident confirmed that the issue has been resolved."
			: "The resident reported that the issue is still present. Please continue handling the report.";
		recipients.forEach(recipient -> createNotification(issue, recipient, type, title, body, sourceEventId));
	}

	private void createNotification(
		Issue issue,
		UUID recipientUserId,
		NotificationType type,
		String title,
		String body,
		UUID sourceEventId
	) {
		NotificationResponse notification = notificationService.createNotification(CreateNotificationCommand.builder()
			.recipientUserId(recipientUserId)
			.communityId(issue.getCommunityId())
			.type(type)
			.category(NotificationCategory.REPORT)
			.priority(NotificationPriority.NORMAL)
			.title(title)
			.body(body)
			.referenceType(NotificationReferenceType.REPORT)
			.referenceId(issue.getId().toString())
			.actionPath("/reports/" + issue.getId())
			.sourceEventId("report-resolution:" + sourceEventId)
			.build());
		eventPublisher.publishEvent(new ReportAssignmentNotificationEvent(
			notification.id(),
			type,
			issue.getId(),
			issue.getCommunityId(),
			recipientUserId,
			ReportAssignmentNotificationEvent.ROUTE_REPORT_DETAILS
		));
	}
}
