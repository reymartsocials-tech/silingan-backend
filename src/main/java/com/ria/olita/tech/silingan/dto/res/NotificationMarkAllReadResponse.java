package com.ria.olita.tech.silingan.dto.res;

import lombok.Builder;

@Builder
public record NotificationMarkAllReadResponse(
	int updatedCount
) {
}
