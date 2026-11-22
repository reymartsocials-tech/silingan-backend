package com.ria.olita.tech.silingan.event;

import java.util.UUID;

import com.ria.olita.tech.silingan.entity.NotificationType;

/**
 * Published inside the report-assignment transaction; consumed only after commit for push delivery.
 * Carries identifiers only so no report content or personal data leaves the database boundary.
 */
public record ReportAssignmentNotificationEvent(
	UUID notificationId,
	NotificationType type,
	UUID reportId,
	UUID communityId,
	UUID recipientUserId,
	String route
) {
	public static final String ROUTE_REPORT_DETAILS = "REPORT_DETAILS";
}
