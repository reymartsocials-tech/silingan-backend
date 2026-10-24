package com.ria.olita.tech.silingan.entity;


import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Represents a user's membership in a community.
 * Consolidates user community membership with optional staff role assignment.
 * 
 * <p>A user can have:
 * <ul>
 *   <li>A basic realm role (SilinganRealmRole) for community participation</li>
 *   <li>Optional staff role assignment (StaffRoleCode) with admin metadata</li>
 * </ul>
 * 
 * <p>Staff fields (roleCode, assignedAt, assignedBy, updatedAt) are only populated
 * when a user is assigned a staff role within the community.
 */
@Entity
@Table(
	name = "user_communities",
	indexes = {
		@Index(name = "idx_uc_user_community", columnList = "user_id, community_id"),
		@Index(name = "idx_uc_community_staff_role", columnList = "community_id, role_code")
	},
	uniqueConstraints = {
		@UniqueConstraint(columnNames = {"user_id", "community_id"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCommunity {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "user_id", insertable = false, updatable = false)
	private UUID userId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "community_id", nullable = false)
	private Community community;

	@Column(name = "community_id", insertable = false, updatable = false)
	private UUID communityId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SilinganRealmRole role;

	@CreationTimestamp
	@Column(name = "joined_at", columnDefinition = "TIMESTAMP")
	private LocalDateTime joinedAt;

	// Staff role fields (optional, populated only when user has staff assignment)
	@Enumerated(EnumType.STRING)
	@Column(name = "role_code", length = 64)
	private StaffRoleCode roleCode;

	@Column(name = "assigned_at", columnDefinition = "TIMESTAMP")
	private LocalDateTime assignedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigned_by", columnDefinition = "UUID")
	private User assignedBy;

	@UpdateTimestamp
	@Column(name = "updated_at", columnDefinition = "TIMESTAMP")
	private LocalDateTime updatedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "profile_picture_attachment_id")
	private Attachment profilePictureAttachment;

	@Column(name = "profile_picture_attachment_id", insertable = false, updatable = false)
	private UUID profilePictureAttachmentId;
}
