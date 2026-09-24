package com.ria.olita.tech.silingan.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
 * Represents a staff member invitation to join a community.
 *
 * <p>Invitations are sent via SMS (mobile) and/or email. The invitation token is used to activate
 * the staff membership but is never exposed in API responses. Invitations expire after 7 days.
 *
 * <p>Status transitions: PENDING → ACCEPTED (on activation), PENDING → EXPIRED (after 7 days),
 * PENDING → REVOKED (by admin).
 */
@Entity
@Table(
	name = "staff_invitations",
	indexes = {
		@Index(name = "idx_si_community_status", columnList = "community_id, status"),
		@Index(name = "idx_si_community_email", columnList = "community_id, email"),
		@Index(name = "idx_si_community_mobile", columnList = "community_id, mobile_number"),
		@Index(name = "idx_si_token", columnList = "token"),
		@Index(name = "idx_si_expires_at", columnList = "expires_at")
	},
	uniqueConstraints = {
		@UniqueConstraint(columnNames = {"community_id", "email", "status"}, 
			name = "uq_si_community_email_status")
	}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffInvitation {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "community_id", nullable = false)
	private Community community;

	@Column(name = "community_id", nullable = false, insertable = false, updatable = false)
	private UUID communityId;

	@Column(nullable = false)
	private String firstName;

	@Column(nullable = false)
	private String lastName;

	@Column(nullable = false, unique = false)
	private String email;

	@Column(name = "mobile_number")
	private String mobileNumber;

	@Column(nullable = false)
	private String position;

	@Enumerated(EnumType.STRING)
	@Column(name = "role_code", nullable = false, length = 64)
	private StaffRoleCode roleCode;

	@Column(name = "invitation_token", nullable = false, unique = true, length = 512)
	private String token;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StaffInvitationStatus status;

	@CreationTimestamp
	@Column(name = "invited_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
	private LocalDateTime invitedAt;

	@Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMP")
	private LocalDateTime expiresAt;

	@Column(name = "accepted_at", columnDefinition = "TIMESTAMP")
	private LocalDateTime acceptedAt;

	@Column(name = "revoked_at", columnDefinition = "TIMESTAMP")
	private LocalDateTime revokedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "invited_by")
	private User invitedBy;

	@Column(name = "invited_by", insertable = false, updatable = false)
	private UUID invitedByUserId;

	@Column(name = "notes", columnDefinition = "TEXT")
	private String notes;

	public boolean isExpired(LocalDateTime now) {
		return expiresAt.isBefore(now) && status == StaffInvitationStatus.PENDING;
	}

	public boolean isActive() {
		return status == StaffInvitationStatus.PENDING && expiresAt.isAfter(LocalDateTime.now());
	}
}
