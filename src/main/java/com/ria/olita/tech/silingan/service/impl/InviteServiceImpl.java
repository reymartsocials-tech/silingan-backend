package com.ria.olita.tech.silingan.service.impl;

import static com.ria.olita.tech.silingan.entity.UserStatus.PENDING;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.req.InvitationRequest;
import com.ria.olita.tech.silingan.dto.res.InvitationSummaryResponse;
import com.ria.olita.tech.silingan.dto.res.InvitationStatsResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.Invitation;
import com.ria.olita.tech.silingan.entity.InvitationStatus;
import com.ria.olita.tech.silingan.entity.InvitationType;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.event.InvitationCreatedEvent;
import com.ria.olita.tech.silingan.exception.ConflictException;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.mapper.InvitationMapper;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.InvitationRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.service.InviteService;
import com.ria.olita.tech.silingan.service.KeycloakService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class InviteServiceImpl implements InviteService {

	private final InvitationRepository invitationRepository;
	private final CommunityRepository communityRepository;
	private final UserRepository userRepository;
	private final UserCommunityRepository userCommunityRepository;
	private final KeycloakService keycloakService;
	private final InvitationMapper invitationMapper;
	private final ApplicationEventPublisher eventPublisher;
	private final PendingUserAndMembershipCreator pendingUserCreator;

	@Value("${app.invitations.expiry-days:7}")
	private int invitationExpiryDays;

	private static final List<String> REQUIRED_ACTIONS = List.of("VERIFY_EMAIL", "UPDATE_PROFILE", "UPDATE_PASSWORD");

	@Override
	public InvitationSummaryResponse invite(UUID communityId, InvitationRequest request) {
		String normalizedEmail = normalizeEmail(request.email());
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		
		StaffRoleCode parsedRoleCode;
		try {
			parsedRoleCode = StaffRoleCode.valueOf(request.roleCode());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid role code: " + request.roleCode(), e);
		}
		
		boolean isAdmin = parsedRoleCode == StaffRoleCode.COMMUNITY_ADMIN;
		
		// Authorization check before database queries
		if (isAdmin) {
			UserContext context = UserContextHolder.get();
			List<SilinganRealmRole> roles = context != null ? context.roles() : List.of();
			log.debug("Admin invitation attempt - checking authorization. UserContext roles: {}, isPlatformAdmin: {}",
				roles, UserContextHolder.isPlatformAdmin());
			
			if (!UserContextHolder.isPlatformAdmin()) {
				throw new ForbiddenException("Only platform admins can invite community admins");
			}
		}
		
		// Validate request (admin or staff) and run existing checks
		if (isAdmin) {
			validateAdminInvitation(communityId, now);
		} else {
			validateStaffInvitation(communityId, normalizedEmail, now);
		}
		
		// Load community (kept because community name is needed)
		Community community = communityRepository.findByIdAndStatus(communityId, CommunityStatus.ACTIVE)
			.orElseThrow(() -> new NotFoundException("Community not found"));
		
		InvitationType invitationType = isAdmin ? InvitationType.ADMIN : InvitationType.STAFF;
		
		// Build invitation
		String inviterId = UserContextHolder.get() != null ? UserContextHolder.get().userId() : null;
		
		Invitation invitation = Invitation.builder()
			.type(invitationType)
			.community(community)
			.communityId(communityId)
			.email(normalizedEmail)
			.keycloakUserId(null) // Will be set after Keycloak creation
			.status(InvitationStatus.PENDING)
			.invitedAt(now)
			.expiresAt(now.plusDays(invitationExpiryDays))
			.notes(request.notes())
			.invitedBy(inviterId != null ? userRepository.getReferenceById(UUID.fromString(inviterId)) : null)
			.build();
		
		// Set roleCode for STAFF invitations
		if (!isAdmin) {
			invitation.setRoleCode(parsedRoleCode);
		}
		
		invitation = invitationRepository.save(invitation);
		
		// Create Keycloak user synchronously before sending email
		String realmRole = isAdmin ? SilinganRealmRole.COMMUNITY_ADMIN.name() : SilinganRealmRole.STAFF.name();
		String keycloakUserId = keycloakService.createInvitationUserWithRoles(
			normalizedEmail,
			List.of(realmRole)
		);
		
		// Update user attributes
		keycloakService.updateUserAttributes(
			keycloakUserId,
			Map.of(
				"communityId", List.of(communityId.toString()),
				"communityName", List.of(community.getName())
			)
		);
		
		// Update invitation with keycloakUserId
		invitation.setKeycloakUserId(keycloakUserId);
		invitation = invitationRepository.save(invitation);
		log.info("Keycloak user created and invitation updated: id={}, keycloakUserId={}, email={}",
			invitation.getId(), keycloakUserId, normalizedEmail);
		
		// Create pending user and community membership
		pendingUserCreator.createPendingUserAndMembership(invitation, keycloakUserId);
		
		// Publish event for async email sending (now with valid keycloakUserId)
		eventPublisher.publishEvent(new InvitationCreatedEvent(
			this,
			invitation.getId(),
			keycloakUserId,
			REQUIRED_ACTIONS,
			invitationType,
			normalizedEmail,
			communityId
		));
		
		log.info("Invitation created - type: {}, community: {}, email: {}, keycloakUserId: {}",
			invitationType, communityId, normalizedEmail, keycloakUserId);
		
		return invitationMapper.toSummaryResponse(invitation);
	}
	
	private void validateAdminInvitation(UUID communityId, LocalDateTime now) {
		// Check if community already has a COMMUNITY_ADMIN
		if (userCommunityRepository.hasRoleInCommunity(communityId, SilinganRealmRole.COMMUNITY_ADMIN)) {
			throw new ConflictException("Community already has a COMMUNITY_ADMIN assigned");
		}
		
		// Check if there's already a pending admin invitation
		if (invitationRepository.hasActivePendingInvitation(communityId, InvitationType.ADMIN, InvitationStatus.PENDING, now)) {
			throw new ConflictException("Community already has a pending administrator invitation");
		}
	}
	
	private void validateStaffInvitation(UUID communityId, String normalizedEmail, LocalDateTime now) {
		// Check if there's already a pending staff invitation for this email
		if (invitationRepository.findActivePendingByEmailAndCommunity(communityId, InvitationType.STAFF, normalizedEmail, InvitationStatus.PENDING, now)
			.isPresent()) {
			throw new ConflictException("A pending invitation already exists for this email in this community");
		}
		
		// Check if user is already an active member (single query replacing findByEmail + UserCommunity lookup)
		if (userCommunityRepository.existsActiveMemberByEmailAndCommunityId(communityId, normalizedEmail)) {
			throw new ConflictException("User is already an active member of this community");
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Page<InvitationSummaryResponse> listInvitations(UUID communityId, InvitationStatus status, String search, Pageable pageable) {
		if (!communityRepository.existsById(communityId)) {
			throw new NotFoundException("Community not found");
		}

		Page<Invitation> invitations;
		if (status != null) {
			invitations = invitationRepository.findByCommunityIdAndStatus(communityId, status, pageable);
		} else {
			invitations = invitationRepository.findByCommunityId(communityId, pageable);
		}

		return invitations.map(invitationMapper::toSummaryResponse);
	}

	@Override
	@Transactional(readOnly = true)
	public InvitationStatsResponse getSummary(UUID communityId) {
		if (!communityRepository.existsById(communityId)) {
			throw new NotFoundException("Community not found");
		}

		long total = invitationRepository.countByCommunityId(communityId);
		long pending = invitationRepository.countByCommunityIdAndStatus(communityId, InvitationStatus.PENDING);
		long accepted = invitationRepository.countByCommunityIdAndStatus(communityId, InvitationStatus.ACCEPTED);
		long expired = invitationRepository.countByCommunityIdAndStatus(communityId, InvitationStatus.EXPIRED);
		long revoked = invitationRepository.countByCommunityIdAndStatus(communityId, InvitationStatus.REVOKED);

		return InvitationStatsResponse.builder()
			.total(total)
			.pending(pending)
			.accepted(accepted)
			.expired(expired)
			.revoked(revoked)
			.build();
	}

	@Override
	@Transactional
	public InvitationSummaryResponse revokeInvitation(UUID communityId, UUID invitationId, String reason) {
		Invitation invitation = invitationRepository.findById(invitationId)
			.orElseThrow(() -> new NotFoundException("Invitation not found"));

		if (!invitation.getCommunityId()
			.equals(communityId)) {
			throw new ForbiddenException("Invitation does not belong to this community");
		}

		if (invitation.getStatus() != InvitationStatus.PENDING) {
			throw new IllegalStateException("Only pending invitations can be revoked");
		}

		invitation.setStatus(InvitationStatus.REVOKED);
		invitation.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
		invitation.setNotes(reason);

		invitation = invitationRepository.save(invitation);
		log.info("Invitation revoked: id={}, community={}, type={}", invitationId, communityId, invitation.getType());

		return invitationMapper.toSummaryResponse(invitation);
	}

	@Override
	@Transactional
	public boolean activateInvitation(String keycloakUserId, UUID communityId, String email) {
		if (keycloakUserId == null || keycloakUserId.isBlank()) {
			log.debug("Invitation activation skipped: missing keycloakUserId");
			return false;
		}

		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		boolean activated = false;

		// Try to activate admin invitation
		var adminInvitation = invitationRepository.findActivePendingAdminInvitation(keycloakUserId, communityId, InvitationStatus.PENDING, now);
		if (adminInvitation.isPresent()) {
			activateAdminInvitation(adminInvitation.get(), keycloakUserId, now);
			activated = true;
		}

		// Try to activate staff invitation
		var staffInvitation = invitationRepository.findPendingByKeycloakUserIdAndType(keycloakUserId, InvitationType.STAFF, InvitationStatus.PENDING, now);
		if (staffInvitation.isPresent()) {
			activateStaffInvitation(staffInvitation.get(), keycloakUserId, now);
			activated = true;
		}

		return activated;
	}

	private void activateAdminInvitation(Invitation invitation, String keycloakUserId, LocalDateTime now) {
		if (invitation.isExpired(now)) {
			invitation.setStatus(InvitationStatus.EXPIRED);
			invitationRepository.save(invitation);
			log.info("Admin invitation {} expired; refusing activation", invitation.getId());
			return;
		}

		if (!keycloakService.isInvitationCompleted(keycloakUserId, REQUIRED_ACTIONS)) {
			log.debug("Admin invitation activation not yet completed for keycloakUserId={}", keycloakUserId);
			return;
		}

		User user = userRepository.findByKeycloakUserId(keycloakUserId)
			.orElse(null);
		if (user == null) {
			log.error("Cannot activate invitation: user not found for keycloakUserId={}", keycloakUserId);
			return;
		}


		// Activate user and membership (change status from PENDING to ACTIVE)
		user.setStatus(com.ria.olita.tech.silingan.entity.UserStatus.ACTIVE);
		userRepository.save(user);

		UUID communityId = invitation.getCommunityId();
		UserCommunity membership = userCommunityRepository
			.findByUserIdAndCommunityId(user.getId(), communityId)
			.orElse(null);

		if (membership != null) {
			userCommunityRepository.save(membership);
		}

		// Mark invitation as accepted
		invitation.setStatus(InvitationStatus.ACCEPTED);
		invitation.setAcceptedAt(now);
		invitationRepository.save(invitation);
		log.info("Admin invitation {} activated for user {}", invitation.getId(), user.getId());
	}

	private void activateStaffInvitation(Invitation invitation, String keycloakUserId, LocalDateTime now) {
		if (invitation.isExpired(now)) {
			invitation.setStatus(InvitationStatus.EXPIRED);
			invitationRepository.save(invitation);
			log.info("Staff invitation {} expired; refusing activation", invitation.getId());
			return;
		}

		if (!keycloakService.isInvitationCompleted(keycloakUserId, REQUIRED_ACTIONS)) {
			log.debug("Staff invitation activation not yet completed for keycloakUserId={}", keycloakUserId);
			return;
		}

		User user = userRepository.findByKeycloakUserId(keycloakUserId)
			.orElse(null);
		if (user == null) {
			log.error("Cannot activate invitation: user not found for keycloakUserId={}", keycloakUserId);
			return;
		}

		// Activate user (change status from PENDING to ACTIVE)
		user.setStatus(com.ria.olita.tech.silingan.entity.UserStatus.ACTIVE);
		userRepository.save(user);

		// Activate membership (change status from PENDING to ACTIVE)
		UUID communityId = invitation.getCommunityId();
		UserCommunity membership = userCommunityRepository
			.findByUserIdAndCommunityId(user.getId(), communityId)
			.orElse(null);

		if (membership != null) {
			userCommunityRepository.save(membership);
		}

		// Mark invitation as accepted
		invitation.setStatus(InvitationStatus.ACCEPTED);
		invitation.setAcceptedAt(now);
		invitationRepository.save(invitation);
		log.info("Staff invitation {} activated for user {}", invitation.getId(), user.getId());
	}

	private String normalizeEmail(String email) {
		return email.trim()
			.toLowerCase(Locale.ROOT);
	}

	/**
	 * Async method to create Keycloak user and update invitation with keycloakUserId.
	 * This runs in a separate thread pool to avoid blocking the invite creation response.
	 * <p>
	 * After successful Keycloak user creation, publishes InvitationCreatedEvent to trigger
	 * email sending with the valid keycloakUserId.
	 */
	@Async
	public void createKeycloakUserAsync(UUID invitationId, String email, String realmRole, UUID communityId, String communityName, InvitationType invitationType) {
		try {
			log.debug("Starting async Keycloak user creation for invitation: {}", invitationId);

			// Create Keycloak user and assign realm role
			String keycloakUserId = keycloakService.createInvitationUserWithRoles(
				email,
				List.of(realmRole)
			);

			// Update user attributes
			keycloakService.updateUserAttributes(
				keycloakUserId,
				Map.of(
					"communityId", List.of(communityId.toString()),
					"communityName", List.of(communityName)
				)
			);

			// Update invitation with keycloakUserId
			Invitation invitation = invitationRepository.findById(invitationId)
				.orElse(null);
			if (invitation != null) {
				invitation.setKeycloakUserId(keycloakUserId);
				invitationRepository.save(invitation);
				log.info("Keycloak user created and invitation updated: id={}, keycloakUserId={}, email={}",
					invitationId, keycloakUserId, email);

				// Create pending user and community membership AFTER Keycloak user is created
				pendingUserCreator.createPendingUserAndMembership(invitation, keycloakUserId);

				// NOW publish event with valid keycloakUserId for email sending
				eventPublisher.publishEvent(new InvitationCreatedEvent(
					this,
					invitationId,
					keycloakUserId,
					REQUIRED_ACTIONS,
					invitationType,
					email,
					communityId
				));
				log.debug("InvitationCreatedEvent published for async email send: keycloakUserId={}, email={}",
					keycloakUserId, email);
			}
		} catch (Exception e) {
			log.error("Failed to create Keycloak user for invitation: {}", invitationId, e);
			// Non-fatal: invitation can still be manually activated or retried later
		}
	}
}


