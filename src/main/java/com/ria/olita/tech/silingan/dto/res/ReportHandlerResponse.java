package com.ria.olita.tech.silingan.dto.res;

import java.time.LocalDateTime;

public record ReportHandlerResponse(
	String displayName,
	String designation,
	String profileImageUrl,
	LocalDateTime assignedAt
) {
}
