package com.ria.olita.tech.silingan.dto.res;

import com.ria.olita.tech.silingan.entity.AnnouncementCategory;
import com.ria.olita.tech.silingan.entity.AnnouncementStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;

@Builder
public record AnnouncementResponse(
	UUID id,
	UUID communityId,
	String title,
	String content,
	AnnouncementCategory category,
	AnnouncementStatus status,
	boolean pinned,
	Instant publishAt,
	LocalDateTime publishedAt,
	UUID publishedBy,
	LocalDateTime createdAt,
	LocalDateTime updatedAt,
	UUID createdBy,
	UUID updatedBy,
	List<AttachmentResponse> attachments
) {
}
