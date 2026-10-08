package com.ria.olita.tech.silingan.dto.req;

import jakarta.validation.constraints.NotBlank;

public record RevokeInvitationRequest(
	@NotBlank(message = "Reason is required")
	String reason
) {
}
