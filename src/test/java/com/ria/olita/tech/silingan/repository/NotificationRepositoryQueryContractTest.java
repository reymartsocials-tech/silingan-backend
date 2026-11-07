package com.ria.olita.tech.silingan.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.ria.olita.tech.silingan.entity.NotificationCategory;
import com.ria.olita.tech.silingan.entity.NotificationType;

class NotificationRepositoryQueryContractTest {

	@Test
	void inboxQueryShouldBeRecipientScopedAndNewestFirst() throws Exception {
		String jpql = queryOf(
			"findInbox",
			UUID.class,
			UUID.class,
			boolean.class,
			NotificationCategory.class,
			NotificationType.class,
			boolean.class,
			boolean.class,
			LocalDateTime.class,
			Pageable.class
		);

		assertThat(jpql).contains("n.recipientUserId = :recipientUserId");
		assertThat(jpql).contains("(:communityId IS NULL OR n.communityId = :communityId)");
		assertThat(jpql).contains("(:unreadOnly = false OR n.readAt IS NULL)");
		assertThat(jpql).contains("ORDER BY n.createdAt DESC, n.id DESC");
	}

	@Test
	void unreadCountQueryShouldCountInDatabaseWithUnreadAndActiveFilters() throws Exception {
		String jpql = queryOf("countUnread", UUID.class, UUID.class, LocalDateTime.class);

		assertThat(jpql).contains("COUNT(n)");
		assertThat(jpql).contains("n.readAt IS NULL");
		assertThat(jpql).contains("n.archivedAt IS NULL");
		assertThat(jpql).contains("n.expiresAt IS NULL OR n.expiresAt > :now");
	}

	@Test
	void markAllReadQueryShouldBeBulkAndIdempotent() throws Exception {
		var method = NotificationRepository.class.getMethod(
			"markAllAsRead",
			UUID.class,
			UUID.class,
			LocalDateTime.class,
			LocalDateTime.class
		);
		Query query = method.getAnnotation(Query.class);

		assertThat(method.getAnnotation(Modifying.class)).isNotNull();
		assertThat(query.value()).contains("UPDATE Notification n");
		assertThat(query.value()).contains("n.readAt IS NULL");
		assertThat(query.value()).contains("n.createdAt <= :operationStartedAt");
	}

	@Test
	void deduplicationQueryShouldUseRecipientAndSourceEvent() throws Exception {
		String jpql = queryOf("findRecipientUserIdsBySourceEventIdAndRecipientUserIdIn", String.class, Collection.class);

		assertThat(jpql).contains("n.sourceEventId = :sourceEventId");
		assertThat(jpql).contains("n.recipientUserId IN :recipientUserIds");
	}

	private String queryOf(String methodName, Class<?>... parameterTypes) throws Exception {
		Query query = NotificationRepository.class.getMethod(methodName, parameterTypes).getAnnotation(Query.class);
		assertThat(query).as("%s must declare @Query", methodName).isNotNull();
		return query.value();
	}
}
