package com.ria.olita.tech.silingan.dto.req;

import java.util.UUID;

import com.ria.olita.tech.silingan.entity.UrgencyLevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateReportRequest(
	@NotNull UUID communityId,
	@NotBlank String title,
	@NotBlank String description,
	@NotNull UUID categoryId,
	String customCategory,
	UrgencyLevel urgencyLevel,
	String location,
	Double latitude,
	Double longitude
) {
}
