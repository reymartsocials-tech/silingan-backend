package com.ria.olita.tech.silingan.dto.req;


import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;


import com.ria.olita.tech.silingan.entity.SilinganRealmRole;

@Builder
public record CreateUserRequest(
	@Schema(example = "jdelacruz") String username,
	@Schema(example = "juan@example.com") @Email String email,
	@Schema(example = "Juan") String firstName,
	@Schema(example = "Dela Cruz") String lastName,
	@Schema(example = "SecurePass123!") String password,
	@NotBlank
	@Pattern(
		regexp = "^(\\+63|0)9\\d{9}$",
		message = "Invalid Philippine mobile number format. Expected: +639XXXXXXXXX or 09XXXXXXXXX"
	)
	@Schema(example = "+639171234567") String mobileNumber,
	@Schema(example = "true", defaultValue = "true") Boolean enabled,
	@Schema(example = "true", defaultValue = "false") Boolean emailVerified,
	@Schema(example = "RESIDENT") SilinganRealmRole communityRole,
	@Schema(
		example = "UDBH-123",
		description = "Manual community code. Supply either this or invitationToken, never both."
	) String communityCode,
	@Schema(
		description = "Raw token from a scanned community join QR or link. The community is resolved "
			+ "server-side; supply either this or communityCode, never both."
	) String invitationToken,
	@Schema(example = "{\"street\":\"123 Main St\",\"barangay\":\"Poblacion\",\"city\":\"Makati\",\"province\":\"Metro Manila\",\"region\":13,\"postalCode\":\"1200\",\"country\":\"Philippines\"}")
	AddressRequest address
) {


	public CreateUserRequest withCommunityRole(SilinganRealmRole communityRole) {
		return new CreateUserRequest(
			this.username,
			this.email,
			this.firstName,
			this.lastName,
			this.password,
			this.mobileNumber,
			this.enabled,
			this.emailVerified,
			communityRole,
			this.communityCode,
			this.invitationToken,
			this.address
		);
	}

	public CreateUserRequest withUsername(String username) {
		return new CreateUserRequest(
			username,
			this.email,
			this.firstName,
			this.lastName,
			this.password,
			this.mobileNumber,
			this.enabled,
			this.emailVerified,
			this.communityRole,
			this.communityCode,
			this.invitationToken,
			this.address
		);
	}

	public CreateUserRequest withEmail(String email) {
		return new CreateUserRequest(
			this.username,
			email,
			this.firstName,
			this.lastName,
			this.password,
			this.mobileNumber,
			this.enabled,
			this.emailVerified,
			this.communityRole,
			this.communityCode,
			this.invitationToken,
			this.address
		);
	}

	/**
	 * Exactly one community-selection mechanism must be supplied: the manual community code used by
	 * existing clients, or an invitation token from a scanned join QR.
	 */
	@AssertTrue(message = "Provide either a community code or an invitation token, but not both")
	@JsonIgnore
	@Schema(hidden = true)
	public boolean isCommunitySelectionValid() {
		boolean hasCode = communityCode != null && !communityCode.isBlank();
		boolean hasToken = invitationToken != null && !invitationToken.isBlank();

		return hasCode ^ hasToken;
	}

	public CreateUserRequest {
		// Set defaults if null
		if (enabled == null) {
			enabled = true;
		}
		if (emailVerified == null) {
			// Only auto-verify email if it's null/auto-generated and user is RESIDENT
			if (email == null || email.isBlank()) {
				emailVerified = (communityRole == null || communityRole == SilinganRealmRole.RESIDENT);
			} else {
				emailVerified = false;
			}
		}
	}


}

