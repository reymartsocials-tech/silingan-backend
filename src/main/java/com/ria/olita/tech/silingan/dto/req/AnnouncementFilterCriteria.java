package com.ria.olita.tech.silingan.dto.req;

import java.util.UUID;

import com.ria.olita.tech.silingan.entity.AnnouncementCategory;
import com.ria.olita.tech.silingan.entity.AnnouncementStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Filter criteria for announcements")
public class AnnouncementFilterCriteria {

	@Schema(description = "Filter by announcement status", example = "PUBLISHED")
	private AnnouncementStatus status;

	@Schema(description = "Filter by announcement category", example = "NOTICE")
	private AnnouncementCategory category;

	@Schema(description = "Filter by pinned status", example = "true")
	private Boolean pinned;

	@Schema(description = "Search keyword in title or content")
	private String keyword;
}
