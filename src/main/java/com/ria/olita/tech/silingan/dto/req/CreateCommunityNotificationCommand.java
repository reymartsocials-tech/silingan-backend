package com.ria.olita.tech.silingan.dto.req;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.NotificationCategory;
import com.ria.olita.tech.silingan.entity.NotificationPriority;
import com.ria.olita.tech.silingan.entity.NotificationReferenceType;
import com.ria.olita.tech.silingan.entity.NotificationType;

import lombok.Builder;

@Builder
public record CreateCommunityNotificationCommand(
	UUID communityId,
	NotificationType type,
	NotificationCategory category,
	NotificationPriority priority,
	String title,
	String body,
	NotificationReferenceType referenceType,
	String referenceId,
	String actionPath,
	String sourceEventId,
	LocalDateTime expiresAt
) {
}
