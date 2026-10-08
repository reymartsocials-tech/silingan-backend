package com.ria.olita.tech.silingan.service.impl;

import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.entity.Invitation;
import com.ria.olita.tech.silingan.entity.InvitationType;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.event.InvitationCreatedEvent;
import com.ria.olita.tech.silingan.repository.InvitationRepository;
import com.ria.olita.tech.silingan.service.KeycloakService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles async Keycloak operations triggered by invitation creation events.
 * Runs asynchronously after transaction commit to avoid proxy issues with self-invocation.
 * This component is separate from InviteServiceImpl to ensure @Async works correctly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvitationKeycloakEventListener {

	private final KeycloakService keycloakService;
	private final InvitationRepository invitationRepository;
	private final PendingUserAndMembershipCreator pendingUserCreator;

	@Async
	@EventListener
	@Transactional
	public void handleInvitationCreated(InvitationCreatedEvent event) {
		try {
			// Only create Keycloak user if not already created
			if (event.getKeycloakUserId() == null) {
				log.debug("Starting async Keycloak user creation for email: {}", event.getEmail());
				createKeycloakUserAndMembership(event);
			}
		} catch (Exception e) {
			log.error("Failed to handle invitation creation event for email: {}", event.getEmail(), e);
			// Non-fatal: invitation can still be manually activated or retried later
		}
	}

	private void createKeycloakUserAndMembership(InvitationCreatedEvent event) {
		String realmRole = getRealmRoleFromInvitationType(event.getInvitationType());

		// Create Keycloak user and assign realm role
		String keycloakUserId = keycloakService.createInvitationUserWithRoles(
			event.getEmail(),
			List.of(realmRole)
		);

		// Get invitation to retrieve community for attributes
		Invitation invitation = invitationRepository.findById(event.getInvitationId())
			.orElse(null);

		if (invitation == null) {
			log.warn("Invitation not found for id: {}", event.getInvitationId());
			return;
		}

		// Update user attributes
		keycloakService.updateUserAttributes(
			keycloakUserId,
			Map.of(
				"communityId", List.of(event.getCommunityId().toString()),
				"communityName", List.of(invitation.getCommunity().getName())
			)
		);

		// Update invitation with keycloakUserId
		invitation.setKeycloakUserId(keycloakUserId);
		invitationRepository.save(invitation);
		log.info("Keycloak user created and invitation updated: invitationId={}, keycloakUserId={}, email={}",
			event.getInvitationId(), keycloakUserId, event.getEmail());

		// Create pending user and community membership
		pendingUserCreator.createPendingUserAndMembership(invitation, keycloakUserId);
	}

	private String getRealmRoleFromInvitationType(InvitationType invitationType) {
		return switch (invitationType) {
			case ADMIN -> SilinganRealmRole.COMMUNITY_ADMIN.name();
			case STAFF -> SilinganRealmRole.STAFF.name();
			default -> throw new IllegalArgumentException("Unknown invitation type: " + invitationType);
		};
	}
}
