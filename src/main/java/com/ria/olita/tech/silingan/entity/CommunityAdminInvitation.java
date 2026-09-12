package com.ria.olita.tech.silingan.entity;

import org.hibernate.annotations.CreationTimestamp;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "community_admin_invitations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommunityAdminInvitation {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "community_id", nullable = false)
	private Community community;

	@Column(name = "community_id", nullable = false, insertable = false, updatable = false)
	private UUID communityId;

	@Column(nullable = false)
	private String email;

	@Column(name = "keycloak_user_id", nullable = false)
	private String keycloakUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CommunityAdminInvitationStatus status;

	@CreationTimestamp
	@Column(name = "invited_at", nullable = false, updatable = false)
	private LocalDateTime invitedAt;

	@Column(name = "accepted_at")
	private LocalDateTime acceptedAt;
}

