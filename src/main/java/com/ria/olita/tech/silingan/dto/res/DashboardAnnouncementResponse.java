package com.ria.olita.tech.silingan.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.AnnouncementCategory;

public record DashboardAnnouncementResponse(
	UUID id,
	String title,
	String contentPreview,
	AnnouncementCategory category,
	boolean pinned,
	LocalDateTime publishedAt
) {
}
