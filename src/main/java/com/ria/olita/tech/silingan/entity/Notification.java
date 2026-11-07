package com.ria.olita.tech.silingan.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.ria.olita.tech.silingan.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SQLRestriction("deleted = false")
public class Notification extends BaseEntity {

	@Id
	@GeneratedValue
	@Column(columnDefinition = "UUID")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recipient_user_id", nullable = false)
	private User recipient;

	@Column(name = "recipient_user_id", insertable = false, updatable = false)
	private UUID recipientUserId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "community_id")
	private Community community;

	@Column(name = "community_id", insertable = false, updatable = false)
	private UUID communityId;

	@Enumerated(EnumType.STRING)
	@Column(name = "notification_type", nullable = false, length = 100)
	private NotificationType notificationType;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 50)
	private NotificationCategory category;

	@Enumerated(EnumType.STRING)
	@Column(name = "priority", nullable = false, length = 30)
	private NotificationPriority priority;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "body", nullable = false, length = 2000)
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(name = "reference_type", length = 50)
	private NotificationReferenceType referenceType;

	@Column(name = "reference_id", length = 100)
	private String referenceId;

	@Column(name = "action_path", length = 500)
	private String actionPath;

	@Column(name = "source_event_id", length = 200)
	private String sourceEventId;

	@Column(name = "read_at", columnDefinition = "TIMESTAMP")
	private LocalDateTime readAt;

	@Column(name = "expires_at", columnDefinition = "TIMESTAMP")
	private LocalDateTime expiresAt;

	@Column(name = "archived_at", columnDefinition = "TIMESTAMP")
	private LocalDateTime archivedAt;
}
