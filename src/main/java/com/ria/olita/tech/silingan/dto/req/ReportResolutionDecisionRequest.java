package com.ria.olita.tech.silingan.dto.req;

import com.ria.olita.tech.silingan.entity.ResolutionDecision;

import jakarta.validation.constraints.NotNull;

public record ReportResolutionDecisionRequest(
	@NotNull ResolutionDecision decision
) {
}
