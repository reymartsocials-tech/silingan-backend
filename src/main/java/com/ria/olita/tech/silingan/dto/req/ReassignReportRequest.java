package com.ria.olita.tech.silingan.dto.req;

import java.util.UUID;

import com.ria.olita.tech.silingan.entity.IssueRoutingDestination;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReassignReportRequest(
	@NotNull IssueRoutingDestination destinationType,
	UUID staffMembershipId,
	@NotBlank @Size(max = 500) String reason
) {
}
