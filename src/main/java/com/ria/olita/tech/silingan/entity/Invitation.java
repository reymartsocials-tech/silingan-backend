package com.ria.olita.tech.silingan.entity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
	name = "invitations",
	indexes = {
		@Index(name = "idx_inv_community_type_status", columnList = "community_id, invitation_type, status"),
		@Index(name = "idx_inv_community_email", columnList = "community_id, email"),
		@Index(name = "idx_inv_expires_at", columnList = "expires_at")
	}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invitation {

	@Id
	@GeneratedValue
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InvitationType type; // STAFF or ADMIN

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "community_id", nullable = false)
	private Community community;

	@Column(name = "community_id", nullable = false, insertable = false, updatable = false)
	private UUID communityId;

	@Column(nullable = false)
	private String email;

	// ===== Admin-specific fields (nullable for staff) =====
	@Column(name = "keycloak_user_id")
	private String keycloakUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "role_code", length = 64)
	private StaffRoleCode roleCode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "invited_by")
	private User invitedBy;

	@Column(name = "invited_by", insertable = false, updatable = false)
	private UUID invitedByUserId;

	@Column(columnDefinition = "TEXT")
	private String notes;

	// ===== Common fields =====
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private InvitationStatus status;

	@Column(name = "invited_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP")
	private LocalDateTime invitedAt;

	@Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMP")
	private LocalDateTime expiresAt;

	@Column(name = "accepted_at", columnDefinition = "TIMESTAMP")
	private LocalDateTime acceptedAt;

	@Column(name = "revoked_at", columnDefinition = "TIMESTAMP")
	private LocalDateTime revokedAt;

	public boolean isExpired(LocalDateTime now) {
		return expiresAt.isBefore(now) && status == InvitationStatus.PENDING;
	}

	public boolean isActive() {
		return isActive(LocalDateTime.now(ZoneOffset.UTC));
	}

	public boolean isActive(LocalDateTime nowUtc) {
		return status == InvitationStatus.PENDING && expiresAt.isAfter(nowUtc);
	}

	public boolean isStaffInvitation() {
		return type == InvitationType.STAFF;
	}

	public boolean isAdminInvitation() {
		return type == InvitationType.ADMIN;
	}

	@PrePersist
	void applyInvitedAtDefault() {
		if (invitedAt == null) {
			invitedAt = LocalDateTime.now(ZoneOffset.UTC);
		}
	}
}
