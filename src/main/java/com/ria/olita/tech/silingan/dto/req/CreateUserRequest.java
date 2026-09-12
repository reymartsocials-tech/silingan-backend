package com.ria.olita.tech.silingan.dto.req;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
	@NotNull
	@Schema(example = "UDBH-123") String communityCode,
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
			this.address
		);
	}

	public CreateUserRequest {
		// Set defaults if null
		if (enabled == null) {
			enabled = true;
		}
		if (emailVerified == null) {
			emailVerified = false;
		}
	}


}

