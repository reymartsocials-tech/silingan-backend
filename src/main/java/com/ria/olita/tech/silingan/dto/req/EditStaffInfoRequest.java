package com.ria.olita.tech.silingan.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Edit staff personal information. Email and staff ID are not editable.
 * Role changes use the separate role assignment endpoint.
 */
public record EditStaffInfoRequest(
	@NotBlank(message = "First name is required")
	@Schema(example = "Juan") String firstName,

	@NotBlank(message = "Last name is required")
	@Schema(example = "Dela Cruz") String lastName,

	@NotBlank(message = "Mobile number is required")
	@Pattern(
		regexp = "^(\\+63|0)9\\d{9}$",
		message = "Invalid Philippine mobile number format. Expected: +639XXXXXXXXX or 09XXXXXXXXX"
	)
	@Schema(example = "+639171234567") String mobileNumber
) {
}
