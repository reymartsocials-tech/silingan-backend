package com.ria.olita.tech.silingan.dto.req;

import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateStaffInvitationRequest(
	@NotBlank(message = "First name is required")
	@Schema(example = "John")
	String firstName,

	@NotBlank(message = "Last name is required")
	@Schema(example = "Doe")
	String lastName,

	@NotBlank(message = "Email is required")
	@Email(message = "Email must be valid")
	@Schema(example = "john.doe@example.com")
	String email,

	@Schema(example = "+639123456789", description = "Optional mobile number")
	@Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Mobile number must be valid E.164 format")
	String mobileNumber,

	@NotBlank(message = "Position is required")
	@Schema(example = "Maintenance Officer")
	String position,

	@NotNull(message = "Role code is required")
	@Schema(example = "PMO_STAFF")
	StaffRoleCode roleCode,

	@Schema(description = "Additional notes for the invitation")
	String notes
) {
}
