package com.ria.olita.tech.silingan.dto.req;

import java.util.UUID;

import com.ria.olita.tech.silingan.entity.NotificationCategory;
import com.ria.olita.tech.silingan.entity.NotificationType;

import lombok.Builder;

@Builder
public record NotificationFilterCriteria(
	UUID communityId,
	Boolean unreadOnly,
	NotificationCategory category,
	NotificationType type,
	Boolean includeArchived
) {
}
