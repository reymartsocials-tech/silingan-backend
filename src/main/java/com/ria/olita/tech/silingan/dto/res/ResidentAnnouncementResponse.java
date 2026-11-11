package com.ria.olita.tech.silingan.dto.res;

import com.ria.olita.tech.silingan.entity.AnnouncementCategory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Builder;

/**
 * Resident-facing view of an announcement. Deliberately omits staff-only and audit information
 * such as status, creator, editor and update timestamps.
 */
@Builder
public record ResidentAnnouncementResponse(
	UUID id,
	String title,
	String content,
	AnnouncementCategory category,
	boolean pinned,
	LocalDateTime publishedAt,
	boolean acknowledged,
	boolean helpful,
	long acknowledgementCount,
	long helpfulCount,
	List<AttachmentResponse> attachments
) {
}
