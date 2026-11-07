package com.ria.olita.tech.silingan.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ria.olita.tech.silingan.entity.Notification;
import com.ria.olita.tech.silingan.entity.NotificationCategory;
import com.ria.olita.tech.silingan.entity.NotificationType;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

	Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

	Optional<Notification> findByRecipientUserIdAndSourceEventId(UUID recipientUserId, String sourceEventId);

	@Query("""
		SELECT n
		FROM Notification n
		WHERE n.recipientUserId = :recipientUserId
			AND (:communityId IS NULL OR n.communityId = :communityId)
			AND (:unreadOnly = false OR n.readAt IS NULL)
			AND (:category IS NULL OR n.category = :category)
			AND (:type IS NULL OR n.notificationType = :type)
			AND (:includeArchived = true OR n.archivedAt IS NULL)
			AND (:activeOnly = false OR n.expiresAt IS NULL OR n.expiresAt > :now)
		ORDER BY n.createdAt DESC, n.id DESC
		""")
	Page<Notification> findInbox(
		@Param("recipientUserId") UUID recipientUserId,
		@Param("communityId") UUID communityId,
		@Param("unreadOnly") boolean unreadOnly,
		@Param("category") NotificationCategory category,
		@Param("type") NotificationType type,
		@Param("includeArchived") boolean includeArchived,
		@Param("activeOnly") boolean activeOnly,
		@Param("now") LocalDateTime now,
		Pageable pageable
	);

	@Query("""
		SELECT COUNT(n)
		FROM Notification n
		WHERE n.recipientUserId = :recipientUserId
			AND (:communityId IS NULL OR n.communityId = :communityId)
			AND n.readAt IS NULL
			AND n.archivedAt IS NULL
			AND (n.expiresAt IS NULL OR n.expiresAt > :now)
		""")
	long countUnread(
		@Param("recipientUserId") UUID recipientUserId,
		@Param("communityId") UUID communityId,
		@Param("now") LocalDateTime now
	);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		UPDATE Notification n
		SET n.readAt = :readAt
		WHERE n.recipientUserId = :recipientUserId
			AND (:communityId IS NULL OR n.communityId = :communityId)
			AND n.readAt IS NULL
			AND n.archivedAt IS NULL
			AND (n.expiresAt IS NULL OR n.expiresAt > :operationStartedAt)
			AND n.createdAt <= :operationStartedAt
		""")
	int markAllAsRead(
		@Param("recipientUserId") UUID recipientUserId,
		@Param("communityId") UUID communityId,
		@Param("readAt") LocalDateTime readAt,
		@Param("operationStartedAt") LocalDateTime operationStartedAt
	);

	@Query("""
		SELECT n.recipientUserId
		FROM Notification n
		WHERE n.sourceEventId = :sourceEventId
			AND n.recipientUserId IN :recipientUserIds
		""")
	Set<UUID> findRecipientUserIdsBySourceEventIdAndRecipientUserIdIn(
		@Param("sourceEventId") String sourceEventId,
		@Param("recipientUserIds") Collection<UUID> recipientUserIds
	);

	@Query("""
		SELECT n
		FROM Notification n
		WHERE n.recipientUserId = :recipientUserId
			AND n.referenceType = :referenceType
			AND n.referenceId = :referenceId
		ORDER BY n.createdAt DESC, n.id DESC
		""")
	Page<Notification> findByRecipientUserIdAndReference(
		@Param("recipientUserId") UUID recipientUserId,
		@Param("referenceType") com.ria.olita.tech.silingan.entity.NotificationReferenceType referenceType,
		@Param("referenceId") String referenceId,
		Pageable pageable
	);
}
