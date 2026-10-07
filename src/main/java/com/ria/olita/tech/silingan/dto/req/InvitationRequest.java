package com.ria.olita.tech.silingan.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Uniform invitation request for both STAFF and ADMIN invitations.
 * 
 * <p>The invited user will provide their profile details (name, address, mobile) during
 * Keycloak's UPDATE_PROFILE required action. This keeps the invitation API simple and
 * ensures users enter their own information accurately.
 */
public record InvitationRequest(
	@NotBlank(message = "Email is required")
	@Email(message = "Email must be valid")
	@Schema(
		example = "john.doe@example.com",
		description = "Email address of the person being invited"
	)
	String email,

	@NotBlank(message = "Role code is required")
	@Schema(
		example = "COMMUNITY_ADMIN",
		description = "Role code: COMMUNITY_ADMIN for admin, or staff role codes (PMO_STAFF, HR_STAFF, etc)"
	)
	String roleCode,

	@Schema(
		description = "Optional context about the invitation (position, team, notes)",
		example = "Senior Maintenance Officer, Manila office"
	)
	String notes
) {
}
