package com.ria.olita.tech.silingan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.ria.olita.tech.silingan.dto.req.CreateBulkNotificationCommand;
import com.ria.olita.tech.silingan.dto.req.CreateNotificationCommand;
import com.ria.olita.tech.silingan.dto.req.NotificationFilterCriteria;
import com.ria.olita.tech.silingan.dto.res.NotificationDispatchResult;
import com.ria.olita.tech.silingan.dto.res.NotificationResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.Notification;
import com.ria.olita.tech.silingan.entity.NotificationCategory;
import com.ria.olita.tech.silingan.entity.NotificationPriority;
import com.ria.olita.tech.silingan.entity.NotificationType;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.mapper.NotificationMapperImpl;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.NotificationRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.security.scope.CommunityScopeGuard;
import com.ria.olita.tech.silingan.security.scope.StaffGrantGuard;
import com.ria.olita.tech.silingan.service.impl.NotificationServiceImpl;

import jakarta.persistence.EntityManager;

class NotificationServiceImplTest {

	private final NotificationRepository notificationRepository = Mockito.mock(NotificationRepository.class);
	private final UserRepository userRepository = Mockito.mock(UserRepository.class);
	private final CommunityRepository communityRepository = Mockito.mock(CommunityRepository.class);
	private final UserCommunityRepository userCommunityRepository = Mockito.mock(UserCommunityRepository.class);
	private final StaffGrantGuard staffGrantGuard = Mockito.mock(StaffGrantGuard.class);
	private final CommunityScopeGuard communityScopeGuard = Mockito.mock(CommunityScopeGuard.class);
	private final EntityManager entityManager = Mockito.mock(EntityManager.class);

	private final NotificationServiceImpl service = new NotificationServiceImpl(
		notificationRepository,
		userRepository,
		communityRepository,
		userCommunityRepository,
		staffGrantGuard,
		communityScopeGuard,
		new NotificationMapperImpl(),
		entityManager
	);

	@BeforeEach
	void setUp() {
		when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
			Notification notification = invocation.getArgument(0);
			if (notification.getId() == null) {
				notification.setId(UUID.randomUUID());
			}
			return notification;
		});
		when(notificationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
		when(entityManager.getReference(eq(User.class), any(UUID.class)))
			.thenAnswer(invocation -> User.builder().id(invocation.getArgument(1)).build());
		when(entityManager.getReference(eq(Community.class), any(UUID.class)))
			.thenAnswer(invocation -> Community.builder().id(invocation.getArgument(1)).build());
	}

	@Test
	void shouldCreateNotification() {
		UUID recipientId = UUID.randomUUID();
		UUID communityId = UUID.randomUUID();
		when(userRepository.existsById(recipientId)).thenReturn(true);
		when(communityRepository.existsById(communityId)).thenReturn(true);

		NotificationResponse response = service.createNotification(CreateNotificationCommand.builder()
			.recipientUserId(recipientId)
			.communityId(communityId)
			.type(NotificationType.ANNOUNCEMENT_PUBLISHED)
			.category(NotificationCategory.ANNOUNCEMENT)
			.priority(NotificationPriority.NORMAL)
			.title("Water advisory")
			.body("No water from 2PM to 5PM")
			.sourceEventId("evt-1")
			.build());

		assertThat(response.id()).isNotNull();
		assertThat(response.type()).isEqualTo(NotificationType.ANNOUNCEMENT_PUBLISHED);
	}

	@Test
	void shouldDeduplicateCreateBySourceEventId() {
		UUID recipientId = UUID.randomUUID();
		UUID existingId = UUID.randomUUID();
		Notification existing = Notification.builder()
			.id(existingId)
			.recipient(User.builder().id(recipientId).build())
			.notificationType(NotificationType.ANNOUNCEMENT_PUBLISHED)
			.category(NotificationCategory.ANNOUNCEMENT)
			.priority(NotificationPriority.NORMAL)
			.title("Water advisory")
			.body("No water from 2PM to 5PM")
			.sourceEventId("evt-1")
			.build();
		when(userRepository.existsById(recipientId)).thenReturn(true);
		when(notificationRepository.findByRecipientUserIdAndSourceEventId(recipientId, "evt-1"))
			.thenReturn(Optional.of(existing));

		NotificationResponse response = service.createNotification(CreateNotificationCommand.builder()
			.recipientUserId(recipientId)
			.type(NotificationType.ANNOUNCEMENT_PUBLISHED)
			.category(NotificationCategory.ANNOUNCEMENT)
			.priority(NotificationPriority.NORMAL)
			.title("Water advisory")
			.body("No water from 2PM to 5PM")
			.sourceEventId("evt-1")
			.build());

		assertThat(response.id()).isEqualTo(existingId);
		verify(notificationRepository, never()).save(any(Notification.class));
	}

	@Test
	void shouldCreateBulkNotificationsWithDeduplication() {
		UUID communityId = UUID.randomUUID();
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		UUID third = UUID.randomUUID();
		when(communityRepository.existsById(communityId)).thenReturn(true);
		when(notificationRepository.findRecipientUserIdsBySourceEventIdAndRecipientUserIdIn("evt-1", List.of(first, second, third)))
			.thenReturn(Set.of(second));

		NotificationDispatchResult result = service.createNotifications(CreateBulkNotificationCommand.builder()
			.recipientUserIds(List.of(first, second, third))
			.communityId(communityId)
			.type(NotificationType.ANNOUNCEMENT_PUBLISHED)
			.category(NotificationCategory.ANNOUNCEMENT)
			.priority(NotificationPriority.NORMAL)
			.title("Notice")
			.body("Body")
			.sourceEventId("evt-1")
			.build());

		assertThat(result.createdCount()).isEqualTo(2);
		assertThat(result.deduplicatedCount()).isEqualTo(1);
	}

	@Test
	void shouldMarkUnreadNotificationAsRead() {
		UUID userId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		when(staffGrantGuard.requireCurrentUserId()).thenReturn(userId);
		when(notificationRepository.findByIdAndRecipientUserId(notificationId, userId))
			.thenReturn(Optional.of(Notification.builder()
				.id(notificationId)
				.recipient(User.builder().id(userId).build())
				.notificationType(NotificationType.REPORT_SUBMITTED)
				.category(NotificationCategory.REPORT)
				.priority(NotificationPriority.NORMAL)
				.title("Report submitted")
				.body("Body")
				.build()));

		NotificationResponse response = service.markCurrentUserNotificationAsRead(notificationId);

		assertThat(response.readAt()).isNotNull();
		verify(notificationRepository).save(any(Notification.class));
	}

	@Test
	void shouldKeepOriginalReadTimestampWhenAlreadyRead() {
		UUID userId = UUID.randomUUID();
		UUID notificationId = UUID.randomUUID();
		LocalDateTime originalReadAt = LocalDateTime.now().minusDays(1);
		Notification notification = Notification.builder()
			.id(notificationId)
			.recipient(User.builder().id(userId).build())
			.notificationType(NotificationType.REPORT_SUBMITTED)
			.category(NotificationCategory.REPORT)
			.priority(NotificationPriority.NORMAL)
			.title("Report submitted")
			.body("Body")
			.readAt(originalReadAt)
			.build();
		when(staffGrantGuard.requireCurrentUserId()).thenReturn(userId);
		when(notificationRepository.findByIdAndRecipientUserId(notificationId, userId))
			.thenReturn(Optional.of(notification));

		NotificationResponse response = service.markCurrentUserNotificationAsRead(notificationId);

		assertThat(response.readAt()).isEqualTo(originalReadAt);
		verify(notificationRepository, never()).save(any(Notification.class));
	}

	@Test
	void shouldRejectMarkReadOfAnotherUsersNotification() {
		UUID userId = UUID.randomUUID();
		when(staffGrantGuard.requireCurrentUserId()).thenReturn(userId);
		when(notificationRepository.findByIdAndRecipientUserId(any(), eq(userId)))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.markCurrentUserNotificationAsRead(UUID.randomUUID()))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void shouldMarkAllAsReadWithinRequestedCommunityInSingleBulkUpdate() {
		UUID userId = UUID.randomUUID();
		UUID communityId = UUID.randomUUID();
		when(staffGrantGuard.requireCurrentUserId()).thenReturn(userId);
		when(notificationRepository.markAllAsRead(eq(userId), eq(communityId), any(), any())).thenReturn(5);

		int updated = service.markAllCurrentUserNotificationsAsRead(communityId);

		assertThat(updated).isEqualTo(5);
		verify(communityScopeGuard).assertAccess(communityId);
	}

	@Test
	void shouldUseDatabaseUnreadCountQuery() {
		UUID userId = UUID.randomUUID();
		UUID communityId = UUID.randomUUID();
		when(staffGrantGuard.requireCurrentUserId()).thenReturn(userId);
		when(notificationRepository.countUnread(eq(userId), eq(communityId), any())).thenReturn(7L);

		long unread = service.getCurrentUserUnreadCount(communityId);

		assertThat(unread).isEqualTo(7L);
	}

	@Test
	void shouldForceDeterministicInboxSort() {
		UUID userId = UUID.randomUUID();
		when(staffGrantGuard.requireCurrentUserId()).thenReturn(userId);
		when(notificationRepository.findInbox(
			eq(userId), eq(null), eq(false), eq(null), eq(null), eq(false), eq(true), any(), any()))
			.thenReturn(new PageImpl<>(List.of()));

		service.getCurrentUserNotifications(NotificationFilterCriteria.builder().build(),
			PageRequest.of(2, 10, org.springframework.data.domain.Sort.by("title")));

		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		verify(notificationRepository).findInbox(
			eq(userId), eq(null), eq(false), eq(null), eq(null), eq(false), eq(true), any(), captor.capture());
		assertThat(captor.getValue().getSort().toString()).contains("createdAt: DESC", "id: DESC");
	}
}
