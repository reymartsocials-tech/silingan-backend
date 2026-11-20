package com.ria.olita.tech.silingan.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.base.BaseEntity;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A revocable, expiring invitation that lets a resident select a community by scanning a QR code or
 * opening a join link instead of typing the community's permanent code.
 *
 * <p>Only the SHA-256 hash of the invitation token is stored; the raw token exists once, in the
 * creation response. The entity deliberately defines no {@code toString}, so neither the hash nor
 * any other field can leak into a log line through string interpolation.
 *
 * <p>The invitation only selects a community. It never authenticates the resident and never
 * substitutes for OTP verification or the existing membership rules.
 */
@Entity
@Table(
	name = "community_join_invitations",
	indexes = {
		@Index(name = "idx_community_join_invitations_community_created", columnList = "community_id, created_at"),
		@Index(name = "idx_community_join_invitations_community_status", columnList = "community_id, status"),
		@Index(name = "idx_community_join_invitations_active_expiration", columnList = "expires_at")
	}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityJoinInvitation extends BaseEntity {

	@Id
	@GeneratedValue
	@Column(columnDefinition = "UUID")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "community_id", nullable = false)
	private Community community;

	@Column(name = "community_id", nullable = false, insertable = false, updatable = false)
	private UUID communityId;

	/** SHA-256 hex digest of the raw token. Never returned by any API. */
	@Column(name = "token_hash", nullable = false, length = 64, unique = true)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "invitation_type", nullable = false, length = 30)
	private CommunityJoinInvitationType invitationType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private CommunityJoinInvitationStatus status;

	@Column(name = "label", length = 150)
	private String label;

	@Enumerated(EnumType.STRING)
	@Column(name = "distribution_channel", length = 30)
	private InvitationDistributionChannel distributionChannel;

	@Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMP")
	private LocalDateTime expiresAt;

	/** {@code null} means unlimited; the MVP always sets a limit. */
	@Column(name = "max_uses")
	private Integer maxUses;

	@Column(name = "used_count", nullable = false)
	@Builder.Default
	private Integer usedCount = 0;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "revoked_by")
	private User revokedBy;

	@Column(name = "revoked_at", columnDefinition = "TIMESTAMP")
	private LocalDateTime revokedAt;

	@Column(name = "last_used_at", columnDefinition = "TIMESTAMP")
	private LocalDateTime lastUsedAt;

	public boolean isExpired(LocalDateTime now) {
		return !expiresAt.isAfter(now);
	}

	public boolean isExhausted() {
		return maxUses != null && usedCount != null && usedCount >= maxUses;
	}

	/**
	 * Lifecycle state as it is right now, independent of whether a scheduled job has updated the
	 * stored status.
	 */
	public CommunityJoinInvitationStatus effectiveStatus(LocalDateTime now) {
		if (status == CommunityJoinInvitationStatus.REVOKED) {
			return CommunityJoinInvitationStatus.REVOKED;
		}
		if (isExpired(now)) {
			return CommunityJoinInvitationStatus.EXPIRED;
		}
		if (isExhausted()) {
			return CommunityJoinInvitationStatus.EXHAUSTED;
		}
		return status;
	}

	public boolean isUsable(LocalDateTime now) {
		return effectiveStatus(now) == CommunityJoinInvitationStatus.ACTIVE;
	}
}
