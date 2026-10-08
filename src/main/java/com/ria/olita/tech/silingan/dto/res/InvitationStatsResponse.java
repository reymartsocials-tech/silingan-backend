package com.ria.olita.tech.silingan.dto.res;

import lombok.Builder;

@Builder
public record InvitationStatsResponse(
	long total,
	long pending,
	long accepted,
	long expired,
	long revoked
) {
}
