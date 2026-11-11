package com.ria.olita.tech.silingan.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

/**
 * Direct staff account creation without invitation.
 * Allows Community Admins to immediately create staff accounts with all required information.
 */
public record CreateStaffAccountRequest(
	@NotBlank(message = "First name is required")
	@Schema(example = "Juan") String firstName,

	@NotBlank(message = "Last name is required")
	@Schema(example = "Dela Cruz") String lastName,

	@NotBlank(message = "Email is required")
	@Email(message = "Email must be valid")
	@Schema(example = "juan@example.com") String email,

	@NotBlank(message = "Mobile number is required")
	@Pattern(
		regexp = "^(\\+63|0)9\\d{9}$",
		message = "Invalid Philippine mobile number format. Expected: +639XXXXXXXXX or 09XXXXXXXXX"
	)
	@Schema(example = "+639171234567") String mobileNumber,

	@NotNull(message = "Staff role is required")
	@Schema(example = "PMO_STAFF") StaffRoleCode staffRole,

	@NotNull(message = "Community code is required")
	@Schema(example = "UDBH-123") String communityCode
) {
}
