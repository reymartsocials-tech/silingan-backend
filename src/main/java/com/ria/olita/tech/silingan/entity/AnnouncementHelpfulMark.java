package com.ria.olita.tech.silingan.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A resident's signal that an announcement was helpful.
 *
 * <p>The resident is referenced through {@link UserCommunity} because that is how the domain
 * identifies a resident inside a community; it keeps the community context on the record. The
 * unique constraint is the authority for "once per resident and announcement" and is what makes
 * concurrent duplicate requests safe.
 */
@Entity
@Table(
	name = "announcement_helpful_marks",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_ann_helpful_announcement_member",
			columnNames = {"announcement_id", "user_community_id"}
		)
	},
	indexes = {
		@Index(name = "idx_ann_helpful_member_announcement", columnList = "user_community_id, announcement_id")
	}
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementHelpfulMark {

	@Id
	@GeneratedValue
	@Column(columnDefinition = "UUID")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "announcement_id", nullable = false)
	private Announcement announcement;

	@Column(name = "announcement_id", insertable = false, updatable = false)
	private UUID announcementId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_community_id", nullable = false)
	private UserCommunity membership;

	@Column(name = "user_community_id", insertable = false, updatable = false)
	private UUID membershipId;

	@CreationTimestamp
	@Column(name = "created_at", columnDefinition = "TIMESTAMP", updatable = false)
	private LocalDateTime createdAt;
}
