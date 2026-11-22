package com.ria.olita.tech.silingan.dto.res;

import java.util.UUID;

public record RoutingStaffSummaryResponse(
	UUID staffMembershipId,
	String displayName,
	String designation,
	boolean eligible
) {
}
