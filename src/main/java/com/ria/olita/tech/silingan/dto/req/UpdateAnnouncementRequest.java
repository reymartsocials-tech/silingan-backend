package com.ria.olita.tech.silingan.dto.req;

import com.ria.olita.tech.silingan.entity.AnnouncementCategory;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Status is deliberately not editable here. Publication is an explicit operation
 * exposed through the publish/unpublish endpoints.
 */
@Builder
public record UpdateAnnouncementRequest(
	@Size(max = 255)
	@Schema(example = "Community General Assembly") String title,
	@Size(max = 10000)
	@Schema(example = "Please attend the general assembly this Saturday at 2PM.") String content,
	@Schema(example = "ADVISORY") AnnouncementCategory category,
	@Schema(example = "false") boolean pinned
) {
}
