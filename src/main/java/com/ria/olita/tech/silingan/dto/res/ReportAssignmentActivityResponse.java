package com.ria.olita.tech.silingan.dto.res;

import java.time.LocalDateTime;

public record ReportAssignmentActivityResponse(
	String title,
	String message,
	LocalDateTime occurredAt
) {
}
