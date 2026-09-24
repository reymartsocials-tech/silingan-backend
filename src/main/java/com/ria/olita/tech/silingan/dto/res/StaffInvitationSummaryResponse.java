package com.ria.olita.tech.silingan.dto.res;

public record StaffInvitationSummaryResponse(
	long total,
	long pending,
	long accepted,
	long expired,
	long revoked
) {
}
