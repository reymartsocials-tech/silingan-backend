package com.ria.olita.tech.silingan.service.impl;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.req.CreateBulkNotificationCommand;
import com.ria.olita.tech.silingan.dto.req.CreateCommunityNotificationCommand;
import com.ria.olita.tech.silingan.dto.req.CreateNotificationCommand;
import com.ria.olita.tech.silingan.dto.req.NotificationFilterCriteria;
import com.ria.olita.tech.silingan.dto.res.NotificationDispatchResult;
import com.ria.olita.tech.silingan.dto.res.NotificationResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.Notification;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.mapper.NotificationMapper;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.NotificationRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.security.scope.CommunityScopeGuard;
import com.ria.olita.tech.silingan.security.scope.StaffGrantGuard;
import com.ria.olita.tech.silingan.service.NotificationService;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

	private static final int BULK_BATCH_SIZE = 200;
	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;
	private static final Sort INBOX_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

	private final NotificationRepository notificationRepository;
	private final UserRepository userRepository;
	private final CommunityRepository communityRepository;
	private final UserCommunityRepository userCommunityRepository;
	private final StaffGrantGuard staffGrantGuard;
	private final CommunityScopeGuard communityScopeGuard;
	private final NotificationMapper notificationMapper;
	private final EntityManager entityManager;

	@Override
	@Transactional
	public NotificationResponse createNotification(CreateNotificationCommand command) {
		validateCreateCommand(command);
		ensureUserExists(command.recipientUserId());
		validateCommunityIfProvided(command.communityId());

		if (hasText(command.sourceEventId())) {
			return notificationRepository
				.findByRecipientUserIdAndSourceEventId(command.recipientUserId(), command.sourceEventId())
				.map(notificationMapper::toResponse)
				.orElseGet(() -> saveSingle(command));
		}

		return saveSingle(command);
	}

	@Override
	@Transactional
	public NotificationDispatchResult createNotifications(CreateBulkNotificationCommand command) {
		validateBulkCommand(command);
		validateCommunityIfProvided(command.communityId());

		List<UUID> uniqueRecipients = new LinkedHashSet<>(command.recipientUserIds()).stream().toList();
		if (uniqueRecipients.isEmpty()) {
			return NotificationDispatchResult.builder().createdCount(0).deduplicatedCount(0).build();
		}

		Set<UUID> alreadyDelivered = hasText(command.sourceEventId())
			? notificationRepository.findRecipientUserIdsBySourceEventIdAndRecipientUserIdIn(
				command.sourceEventId(),
				uniqueRecipients
			)
			: Set.of();

		List<UUID> recipientsToInsert = uniqueRecipients.stream()
			.filter(recipientId -> !alreadyDelivered.contains(recipientId))
			.toList();

		int createdCount = 0;
		for (int start = 0; start < recipientsToInsert.size(); start += BULK_BATCH_SIZE) {
			int end = Math.min(start + BULK_BATCH_SIZE, recipientsToInsert.size());
			List<UUID> batchRecipientIds = recipientsToInsert.subList(start, end);
			List<Notification> notifications = batchRecipientIds.stream()
				.map(recipientId -> buildNotification(command, recipientId))
				.toList();
			try {
				notificationRepository.saveAll(notifications);
				createdCount += notifications.size();
			} catch (DataIntegrityViolationException ex) {
				if (!hasText(command.sourceEventId())) {
					throw ex;
				}
				log.debug("Deduplication prevented duplicate notifications for sourceEventId={}", command.sourceEventId());
			}
		}

		return NotificationDispatchResult.builder()
			.createdCount(createdCount)
			.deduplicatedCount(uniqueRecipients.size() - createdCount)
			.build();
	}

	@Override
	@Transactional
	public NotificationDispatchResult createNotificationsForCommunityResidents(CreateCommunityNotificationCommand command) {
		validateCommunityNotificationCommand(command);
		validateCommunityIfProvided(command.communityId());

		int created = 0;
		int deduplicated = 0;
		int pageNumber = 0;

		while (true) {
			List<UUID> residentIds = userCommunityRepository.findActiveUserIdsByCommunityIdAndRole(
				command.communityId(),
				SilinganRealmRole.RESIDENT,
				PageRequest.of(pageNumber, BULK_BATCH_SIZE)
			);
			if (residentIds.isEmpty()) {
				break;
			}

			NotificationDispatchResult result = createNotifications(CreateBulkNotificationCommand.builder()
				.recipientUserIds(residentIds)
				.communityId(command.communityId())
				.type(command.type())
				.category(command.category())
				.priority(command.priority())
				.title(command.title())
				.body(command.body())
				.referenceType(command.referenceType())
				.referenceId(command.referenceId())
				.actionPath(command.actionPath())
				.sourceEventId(command.sourceEventId())
				.expiresAt(command.expiresAt())
				.build());
			created += result.createdCount();
			deduplicated += result.deduplicatedCount();

			if (residentIds.size() < BULK_BATCH_SIZE) {
				break;
			}
			pageNumber++;
		}

		return NotificationDispatchResult.builder()
			.createdCount(created)
			.deduplicatedCount(deduplicated)
			.build();
	}

	@Override
	public Page<NotificationResponse> getCurrentUserNotifications(NotificationFilterCriteria criteria, Pageable pageable) {
		UUID currentUserId = staffGrantGuard.requireCurrentUserId();
		NotificationFilterCriteria safeCriteria = criteria == null ? NotificationFilterCriteria.builder().build() : criteria;
		UUID scopedCommunityId = resolveAndValidateCommunityScope(safeCriteria.communityId());
		boolean unreadOnly = Boolean.TRUE.equals(safeCriteria.unreadOnly());
		boolean includeArchived = Boolean.TRUE.equals(safeCriteria.includeArchived());

		return notificationRepository.findInbox(
				currentUserId,
				scopedCommunityId,
				unreadOnly,
				safeCriteria.category(),
				safeCriteria.type(),
				includeArchived,
				true,
				LocalDateTime.now(),
				toInboxPageable(pageable)
			)
			.map(notificationMapper::toResponse);
	}

	@Override
	public long getCurrentUserUnreadCount(UUID communityId) {
		UUID currentUserId = staffGrantGuard.requireCurrentUserId();
		UUID scopedCommunityId = resolveAndValidateCommunityScope(communityId);
		return notificationRepository.countUnread(currentUserId, scopedCommunityId, LocalDateTime.now());
	}

	@Override
	@Transactional
	public NotificationResponse markCurrentUserNotificationAsRead(UUID notificationId) {
		if (notificationId == null) {
			throw new ValidationException("Notification ID is required");
		}
		UUID currentUserId = staffGrantGuard.requireCurrentUserId();
		Notification notification = notificationRepository.findByIdAndRecipientUserId(notificationId, currentUserId)
			.orElseThrow(() -> new NotFoundException("Notification not found"));

		assertCommunityAccessForNotification(notification);
		if (notification.getReadAt() == null) {
			notification.setReadAt(LocalDateTime.now());
			notificationRepository.save(notification);
		}
		return notificationMapper.toResponse(notification);
	}

	@Override
	@Transactional
	public int markAllCurrentUserNotificationsAsRead(UUID communityId) {
		UUID currentUserId = staffGrantGuard.requireCurrentUserId();
		UUID scopedCommunityId = resolveAndValidateCommunityScope(communityId);
		LocalDateTime boundary = LocalDateTime.now();
		return notificationRepository.markAllAsRead(currentUserId, scopedCommunityId, boundary, boundary);
	}

	@Override
	@Transactional
	public NotificationResponse archiveCurrentUserNotification(UUID notificationId) {
		if (notificationId == null) {
			throw new ValidationException("Notification ID is required");
		}
		UUID currentUserId = staffGrantGuard.requireCurrentUserId();
		Notification notification = notificationRepository.findByIdAndRecipientUserId(notificationId, currentUserId)
			.orElseThrow(() -> new NotFoundException("Notification not found"));
		assertCommunityAccessForNotification(notification);

		if (notification.getArchivedAt() == null) {
			notification.setArchivedAt(LocalDateTime.now());
			notificationRepository.save(notification);
		}
		return notificationMapper.toResponse(notification);
	}

	private NotificationResponse saveSingle(CreateNotificationCommand command) {
		try {
			Notification saved = notificationRepository.save(buildNotification(command, command.recipientUserId()));
			return notificationMapper.toResponse(saved);
		} catch (DataIntegrityViolationException ex) {
			if (!hasText(command.sourceEventId())) {
				throw ex;
			}
			return notificationRepository
				.findByRecipientUserIdAndSourceEventId(command.recipientUserId(), command.sourceEventId())
				.map(notificationMapper::toResponse)
				.orElseThrow(() -> ex);
		}
	}

	private Notification buildNotification(CreateNotificationCommand command, UUID recipientUserId) {
		return Notification.builder()
			.recipient(entityManager.getReference(User.class, recipientUserId))
			.community(command.communityId() == null ? null : entityManager.getReference(Community.class, command.communityId()))
			.notificationType(command.type())
			.category(command.category())
			.priority(command.priority())
			.title(command.title().trim())
			.body(command.body().trim())
			.referenceType(command.referenceType())
			.referenceId(trimToNull(command.referenceId()))
			.actionPath(trimToNull(command.actionPath()))
			.sourceEventId(trimToNull(command.sourceEventId()))
			.expiresAt(command.expiresAt())
			.build();
	}

	private Notification buildNotification(CreateBulkNotificationCommand command, UUID recipientUserId) {
		return Notification.builder()
			.recipient(entityManager.getReference(User.class, recipientUserId))
			.community(command.communityId() == null ? null : entityManager.getReference(Community.class, command.communityId()))
			.notificationType(command.type())
			.category(command.category())
			.priority(command.priority())
			.title(command.title().trim())
			.body(command.body().trim())
			.referenceType(command.referenceType())
			.referenceId(trimToNull(command.referenceId()))
			.actionPath(trimToNull(command.actionPath()))
			.sourceEventId(trimToNull(command.sourceEventId()))
			.expiresAt(command.expiresAt())
			.build();
	}

	private void validateCreateCommand(CreateNotificationCommand command) {
		if (command == null) {
			throw new ValidationException("Notification command is required");
		}
		if (command.recipientUserId() == null) {
			throw new ValidationException("Recipient user ID is required");
		}
		validateCommonFields(command.type(), command.category(), command.priority(), command.title(), command.body());
	}

	private void validateBulkCommand(CreateBulkNotificationCommand command) {
		if (command == null) {
			throw new ValidationException("Bulk notification command is required");
		}
		if (command.recipientUserIds() == null || command.recipientUserIds().isEmpty()) {
			throw new ValidationException("Recipient user IDs are required");
		}
		validateCommonFields(command.type(), command.category(), command.priority(), command.title(), command.body());
	}

	private void validateCommunityNotificationCommand(CreateCommunityNotificationCommand command) {
		if (command == null) {
			throw new ValidationException("Community notification command is required");
		}
		if (command.communityId() == null) {
			throw new ValidationException("Community ID is required");
		}
		validateCommonFields(command.type(), command.category(), command.priority(), command.title(), command.body());
	}

	private void validateCommonFields(Object type, Object category, Object priority, String title, String body) {
		if (type == null) {
			throw new ValidationException("Notification type is required");
		}
		if (category == null) {
			throw new ValidationException("Notification category is required");
		}
		if (priority == null) {
			throw new ValidationException("Notification priority is required");
		}
		requireText(title, "Title");
		requireText(body, "Body");
	}

	private String requireText(String value, String fieldName) {
		if (!hasText(value)) {
			throw new ValidationException(fieldName + " must not be blank");
		}
		return value.trim();
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private String trimToNull(String value) {
		if (!hasText(value)) {
			return null;
		}
		return value.trim();
	}

	private void ensureUserExists(UUID userId) {
		if (!userRepository.existsById(userId)) {
			throw new NotFoundException("Recipient user not found");
		}
	}

	private void validateCommunityIfProvided(UUID communityId) {
		if (communityId == null) {
			return;
		}
		if (!communityRepository.existsById(communityId)) {
			throw new NotFoundException("Community not found");
		}
	}

	private UUID resolveAndValidateCommunityScope(UUID requestedCommunityId) {
		UUID scopedCommunityId = requestedCommunityId != null ? requestedCommunityId : selectedCommunityFromContext();
		if (scopedCommunityId != null && !UserContextHolder.isPlatformAdmin()) {
			communityScopeGuard.assertAccess(scopedCommunityId);
		}
		return scopedCommunityId;
	}

	private UUID selectedCommunityFromContext() {
		UserContext context = UserContextHolder.get();
		if (context == null || context.communityId() == null || context.communityId().isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(context.communityId());
		} catch (IllegalArgumentException ex) {
			throw new ForbiddenException("Invalid authenticated user context");
		}
	}

	private Pageable toInboxPageable(Pageable pageable) {
		if (pageable == null) {
			return PageRequest.of(0, DEFAULT_PAGE_SIZE, INBOX_SORT);
		}

		int page = Math.max(pageable.getPageNumber(), 0);
		int size = pageable.isUnpaged() ? DEFAULT_PAGE_SIZE : pageable.getPageSize();
		size = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		return PageRequest.of(page, size, INBOX_SORT);
	}

	private void assertCommunityAccessForNotification(Notification notification) {
		if (notification.getCommunityId() == null || UserContextHolder.isPlatformAdmin()) {
			return;
		}
		communityScopeGuard.assertAccess(notification.getCommunityId());
	}
}
