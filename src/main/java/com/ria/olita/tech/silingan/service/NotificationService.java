package com.ria.olita.tech.silingan.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ria.olita.tech.silingan.dto.req.CreateBulkNotificationCommand;
import com.ria.olita.tech.silingan.dto.req.CreateCommunityNotificationCommand;
import com.ria.olita.tech.silingan.dto.req.CreateNotificationCommand;
import com.ria.olita.tech.silingan.dto.req.NotificationFilterCriteria;
import com.ria.olita.tech.silingan.dto.res.NotificationDispatchResult;
import com.ria.olita.tech.silingan.dto.res.NotificationResponse;

public interface NotificationService {

	NotificationResponse createNotification(CreateNotificationCommand command);

	NotificationDispatchResult createNotifications(CreateBulkNotificationCommand command);

	NotificationDispatchResult createNotificationsForCommunityResidents(CreateCommunityNotificationCommand command);

	Page<NotificationResponse> getCurrentUserNotifications(NotificationFilterCriteria criteria, Pageable pageable);

	long getCurrentUserUnreadCount(UUID communityId);

	NotificationResponse markCurrentUserNotificationAsRead(UUID notificationId);

	int markAllCurrentUserNotificationsAsRead(UUID communityId);

	NotificationResponse archiveCurrentUserNotification(UUID notificationId);
}
