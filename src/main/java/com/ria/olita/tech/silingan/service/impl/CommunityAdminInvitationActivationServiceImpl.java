package com.ria.olita.tech.silingan.service.impl;

import com.ria.olita.tech.silingan.entity.CommunityAdminInvitation;
import com.ria.olita.tech.silingan.entity.CommunityAdminInvitationStatus;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.repository.CommunityAdminInvitationRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.service.CommunityAdminInvitationActivationService;
import com.ria.olita.tech.silingan.service.KeycloakService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityAdminInvitationActivationServiceImpl implements CommunityAdminInvitationActivationService {

	private static final List<String> ADMIN_REQUIRED_ACTIONS = List.of("VERIFY_EMAIL", "UPDATE_PROFILE", "UPDATE_PASSWORD");

	private final CommunityAdminInvitationRepository invitationRepository;
	private final UserRepository userRepository;
	private final UserCommunityRepository userCommunityRepository;
	private final KeycloakService keycloakService;

	@Override
	@Transactional
	public void activateIfCompleted(String keycloakUserId, UUID communityId ) {
		if (keycloakUserId == null || keycloakUserId.isBlank()) {
			log.debug("Invitation activation skipped: missing keycloakUserId");
			return;
		}

		log.debug("Invitation activation check triggered for keycloakUserId={}", keycloakUserId);

		List<CommunityAdminInvitation> pendingInvitations = invitationRepository.findByKeycloakUserIdAndStatusAndCommunityId(
			keycloakUserId,
			CommunityAdminInvitationStatus.PENDING,
			communityId
		);
		if (pendingInvitations.isEmpty()) {
			log.debug("Invitation activation skipped: no PENDING invitations for keycloakUserId={}", keycloakUserId);
			return;
		}
		log.info("Found {} pending admin invitation(s) for keycloakUserId={}", pendingInvitations.size(), keycloakUserId);

		if (!keycloakService.isInvitationCompleted(keycloakUserId, ADMIN_REQUIRED_ACTIONS)) {
			log.info("Invitation activation not yet completed for keycloakUserId={} (required actions still pending)", keycloakUserId);
			return;
		}
		log.info("Invitation marked complete in Keycloak for keycloakUserId={}; activating local records", keycloakUserId);

		User user = userRepository.findByKeycloakUserId(keycloakUserId)
			.orElseGet(() -> createUserFromInvitation(keycloakUserId, pendingInvitations.getFirst()));

		keycloakService.assignRealmRole(keycloakUserId, SilinganRealmRole.COMMUNITY_ADMIN.name());

		for (CommunityAdminInvitation invitation : pendingInvitations) {
			if (userCommunityRepository.hasRoleInCommunity(invitation.getCommunity().getId(), SilinganRealmRole.COMMUNITY_ADMIN)) {
				log.warn("Community {} already has COMMUNITY_ADMIN; expiring invitation {}", invitation.getCommunity().getId(), invitation.getId());
				invitation.setStatus(CommunityAdminInvitationStatus.EXPIRED);
				continue;
			}

			UserCommunity membership = userCommunityRepository
				.findByUserIdAndCommunityId(user.getId(), invitation.getCommunity().getId())
				.orElseGet(() -> UserCommunity.builder()
					.user(user)
					.community(invitation.getCommunity())
					.build());
			membership.setRole(SilinganRealmRole.COMMUNITY_ADMIN);
			userCommunityRepository.save(membership);

			invitation.setStatus(CommunityAdminInvitationStatus.ACCEPTED);
			invitation.setAcceptedAt(LocalDateTime.now());
			log.info("Invitation {} accepted for user {} in community {}", invitation.getId(), user.getId(), invitation.getCommunity().getId());
		}
	}

	private User createUserFromInvitation(String keycloakUserId, CommunityAdminInvitation invitation) {
		User user = User.builder()
			.keycloakUserId(keycloakUserId)
			.username(invitation.getEmail())
			.email(invitation.getEmail())
			.build();
		log.info("Creating local user record for accepted community admin invitation: {}", invitation.getEmail());
		user = userRepository.save(user);
		return user;
	}
}

