package com.ria.olita.tech.silingan.event.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ria.olita.tech.silingan.entity.NotificationType;
import com.ria.olita.tech.silingan.event.ReportAssignmentNotificationEvent;

class ReportAssignmentPushListenerTest {

	@Test
	void shouldSwallowPushFailureSoCommittedReportIsUnaffected() {
		ReportAssignmentPushListener listener = spy(new ReportAssignmentPushListener());
		doThrow(new IllegalStateException("provider unavailable")).when(listener).deliver(any());

		assertThatCode(() -> listener.onReportAssignmentNotification(new ReportAssignmentNotificationEvent(
			UUID.randomUUID(), NotificationType.REPORT_ASSIGNED, UUID.randomUUID(), UUID.randomUUID(),
			UUID.randomUUID(), ReportAssignmentNotificationEvent.ROUTE_REPORT_DETAILS)))
			.doesNotThrowAnyException();
	}
}
