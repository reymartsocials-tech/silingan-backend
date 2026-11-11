package com.ria.olita.tech.silingan.service.impl;

import static com.ria.olita.tech.silingan.entity.UserStatus.PENDING;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.entity.Invitation;
import com.ria.olita.tech.silingan.entity.InvitationType;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Component for creating pending user and community membership records.
 * Separated from InviteServiceImpl to support event-driven async operations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingUserAndMembershipCreator {

	private final UserRepository userRepository;
	private final UserCommunityRepository userCommunityRepository;

	/**
	 * Creates a pending user and community membership after Keycloak user creation.
	 *
	 * @param invitation the invitation entity with populated community
	 * @param keycloakUserId the newly created Keycloak user ID
	 */
	@Transactional
	public void createPendingUserAndMembership(Invitation invitation, String keycloakUserId) {
		try {
			// Check if user already exists
			User user = userRepository.findByKeycloakUserId(keycloakUserId)
				.orElseGet(() -> {
					// Create user with PENDING status (will be activated on first login)
					// Set lastSelectedCommunity to the invitation's community so it's pre-selected
					User newUser = User.builder()
						.keycloakUserId(keycloakUserId)
						.username(invitation.getEmail())
						.email(invitation.getEmail())
						.lastSelectedCommunity(invitation.getCommunity())
						.status(PENDING)
						.build();
					log.info("Creating pending user record during invitation: {} (selectedCommunity={})",
						invitation.getEmail(), invitation.getCommunityId());
					return userRepository.save(newUser);
				});

			// Create community membership
			userCommunityRepository
				.findByUserIdAndCommunityId(user.getId(), invitation.getCommunityId())
				.orElseGet(() -> {
					UserCommunity.UserCommunityBuilder builder = UserCommunity.builder()
						.user(user)
						.community(invitation.getCommunity())
						.role(invitation.getType() == InvitationType.ADMIN
							? SilinganRealmRole.COMMUNITY_ADMIN
							: SilinganRealmRole.STAFF);
					
					// Populate staff role fields if invitation has roleCode
					if (invitation.getRoleCode() != null) {
						builder
							.roleCode(invitation.getRoleCode())
							.assignedAt(LocalDateTime.now(ZoneOffset.UTC))
							.assignedBy(invitation.getInvitedBy());
					}
					
					UserCommunity newMembership = builder.build();
					log.info("Creating pending community membership during invitation: user={}, community={}, role={}, staffRole={}",
						user.getId(), invitation.getCommunityId(), newMembership.getRole(), newMembership.getRoleCode());
					return userCommunityRepository.save(newMembership);
				});

			log.debug("Pending user and membership created for invitation: {}", invitation.getId());
		} catch (Exception e) {
			log.error("Failed to create pending user and membership for invitation {}", invitation.getId(), e);
			// Non-fatal: invitation will still activate on first login
		}
	}
}
