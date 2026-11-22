package com.ria.olita.tech.silingan.event.listener;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ria.olita.tech.silingan.event.ReportAssignmentNotificationEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * Secondary delivery channel for report-assignment notifications. Runs only after the report
 * transaction commits, so the persisted database notification remains the source of truth and a
 * delivery failure can never roll back the report.
 */
@Component
@Slf4j
public class ReportAssignmentPushListener {

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onReportAssignmentNotification(ReportAssignmentNotificationEvent event) {
		try {
			deliver(event);
		} catch (Exception ex) {
			log.warn("Push delivery failed - notificationId: {}, reportId: {}",
				event.notificationId(), event.reportId(), ex);
		}
	}

	void deliver(ReportAssignmentNotificationEvent event) {
		// No push provider or device-token registry is integrated yet; the in-app notification is already persisted.
		log.info("Push delivery queued - notificationId: {}, type: {}, reportId: {}, communityId: {}, route: {}",
			event.notificationId(), event.type(), event.reportId(), event.communityId(), event.route());
	}
}
