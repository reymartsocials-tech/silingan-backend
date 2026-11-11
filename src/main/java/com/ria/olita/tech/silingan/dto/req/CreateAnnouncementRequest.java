package com.ria.olita.tech.silingan.dto.req;

import com.ria.olita.tech.silingan.entity.AnnouncementCategory;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.UUID;

@Builder
public record CreateAnnouncementRequest(

	@NotNull
	@Schema(example = "550e8400-e29b-41d4-a716-446655440000") UUID communityId,

	@NotBlank
	@Size(max = 255)
	@Schema(example = "Community General Assembly") String title,

	@NotBlank
	@Size(max = 10000)
	@Schema(example = "Please attend the general assembly this Saturday at 2PM.") String content,

	@NotNull
	@Schema(example = "NOTICE") AnnouncementCategory category,

	@Schema(example = "false") boolean pinned
) {
}
