package com.ria.olita.tech.silingan.dto.req;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReportRequest(
	@NotNull UUID communityId,
	@NotBlank String title,
	@NotBlank String description,
	@NotNull UUID categoryId,
	String customCategory,
	String imageUrl,
	String location,
	Double latitude,
	Double longitude
) {
}
