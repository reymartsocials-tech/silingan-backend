package com.ria.olita.tech.silingan.dto.req;

import com.ria.olita.tech.silingan.entity.DirectoryContactCategory;
import com.ria.olita.tech.silingan.entity.DirectoryContactType;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Community ownership and the audit fields are absent by design: they are server managed and a
 * contact can never be moved to another community through an update. Active status is changed
 * through the dedicated activate and deactivate endpoints.
 */
@Builder
public record UpdateDirectoryContactRequest(

	@NotBlank(message = "Display name is required")
	@Size(max = 150)
	@Schema(example = "Barangay Emergency Hotline") String displayName,

	@NotNull(message = "Category is required")
	@Schema(example = "EMERGENCY_SERVICE") DirectoryContactCategory category,

	@NotNull(message = "Contact type is required")
	@Schema(example = "BARANGAY_EMERGENCY_RESPONSE") DirectoryContactType contactType,

	@NotBlank(message = "Primary phone number is required")
	@Size(min = 3, max = 32)
	@Pattern(
		regexp = "^[0-9+()\\-.\\s]+$",
		message = "Phone number may only contain digits, spaces and the characters + ( ) - ."
	)
	@Schema(example = "+63288881234") String primaryPhoneNumber,

	@Size(min = 3, max = 32)
	@Pattern(
		regexp = "^[0-9+()\\-.\\s]+$",
		message = "Phone number may only contain digits, spaces and the characters + ( ) - ."
	)
	@Schema(example = "911") String secondaryPhoneNumber,

	@Size(max = 500)
	@Schema(example = "Barangay response team for medical and fire emergencies") String description,

	@Size(max = 150)
	@Schema(example = "24/7") String operatingHours,

	@Size(max = 255)
	@Schema(example = "Barangay Hall, Main Street") String address,

	@Email(message = "Email address must be valid")
	@Size(max = 255)
	@Schema(example = "emergency@barangay.gov.ph") String email,

	@PositiveOrZero(message = "Display order must not be negative")
	@Schema(example = "1") Integer displayOrder
) {
}
