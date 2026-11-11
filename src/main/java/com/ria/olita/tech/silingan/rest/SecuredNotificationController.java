package com.ria.olita.tech.silingan.rest;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.req.NotificationFilterCriteria;
import com.ria.olita.tech.silingan.dto.res.ApiResponse;
import com.ria.olita.tech.silingan.dto.res.NotificationMarkAllReadResponse;
import com.ria.olita.tech.silingan.dto.res.NotificationResponse;
import com.ria.olita.tech.silingan.dto.res.NotificationUnreadCountResponse;
import com.ria.olita.tech.silingan.entity.NotificationCategory;
import com.ria.olita.tech.silingan.entity.NotificationType;
import com.ria.olita.tech.silingan.service.NotificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/secure/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Notification", description = "In-app notification APIs")
public class SecuredNotificationController {

	private final NotificationService notificationService;

	@GetMapping
	@Operation(summary = "Get notifications of the authenticated user")
	public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
		@RequestParam(required = false) UUID communityId,
		@RequestParam(required = false) Boolean unreadOnly,
		@RequestParam(required = false) NotificationCategory category,
		@RequestParam(required = false) NotificationType type,
		@RequestParam(required = false) Boolean includeArchived,
		Pageable pageable
	) {
		NotificationFilterCriteria criteria = NotificationFilterCriteria.builder()
			.communityId(communityId)
			.unreadOnly(unreadOnly)
			.category(category)
			.type(type)
			.includeArchived(includeArchived)
			.build();
		return ResponseEntity.ok(ApiResponse.success(notificationService.getCurrentUserNotifications(criteria, pageable)));
	}

	@GetMapping("/unread-count")
	@Operation(summary = "Get unread notification count of the authenticated user")
	public ResponseEntity<ApiResponse<NotificationUnreadCountResponse>> getUnreadCount(
		@RequestParam(required = false) UUID communityId
	) {
		return ResponseEntity.ok(ApiResponse.success(NotificationUnreadCountResponse.builder()
			.unreadCount(notificationService.getCurrentUserUnreadCount(communityId))
			.build()));
	}

	@PutMapping("/{notificationId}/read")
	@Operation(summary = "Mark one notification as read")
	public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(@PathVariable UUID notificationId) {
		return ResponseEntity.ok(ApiResponse.success(notificationService.markCurrentUserNotificationAsRead(notificationId)));
	}

	@PutMapping("/read-all")
	@Operation(summary = "Mark all eligible notifications as read")
	public ResponseEntity<ApiResponse<NotificationMarkAllReadResponse>> markAllAsRead(
		@RequestParam(required = false) UUID communityId
	) {
		return ResponseEntity.ok(ApiResponse.success(NotificationMarkAllReadResponse.builder()
			.updatedCount(notificationService.markAllCurrentUserNotificationsAsRead(communityId))
			.build()));
	}

	@PutMapping("/{notificationId}/archive")
	@Operation(summary = "Archive one notification")
	public ResponseEntity<ApiResponse<NotificationResponse>> archive(@PathVariable UUID notificationId) {
		return ResponseEntity.ok(ApiResponse.success(notificationService.archiveCurrentUserNotification(notificationId)));
	}
}
