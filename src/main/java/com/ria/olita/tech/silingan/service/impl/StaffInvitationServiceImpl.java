package com.ria.olita.tech.silingan.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.req.CreateStaffInvitationRequest;
import com.ria.olita.tech.silingan.dto.res.StaffInvitationResponse;
import com.ria.olita.tech.silingan.dto.res.StaffInvitationSummaryResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.StaffInvitation;
import com.ria.olita.tech.silingan.entity.StaffInvitationStatus;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.UserCommunityStaffRole;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.StaffInvitationRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityStaffRoleRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.service.KeycloakService;
import com.ria.olita.tech.silingan.service.StaffInvitationService;
import com.ria.olita.tech.silingan.service.email.InvitationEmailContext;
import com.ria.olita.tech.silingan.service.sms.SmsService;
import com.ria.olita.tech.silingan.util.TokenGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Staff invitation service managing lifecycle of staff member invitations.
 *
 * <p><b>Token security:</b> Tokens are generated securely, stored in the database, and
 * never exposed via API responses. Only used internally or in activation links.
 *
 * <p><b>Expiration:</b> Invitations expire 7 days after creation (UTC). The scheduled task
 * {@link StaffInvitationExpirationScheduler} runs daily to mark expired pending invitations.
 *
 * <p><b>Idempotency:</b> expireOldInvitations() is safe to call multiple times.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffInvitationServiceImpl implements StaffInvitationService {

	private static final int INVITATION_EXPIRY_DAYS = 7;
	private static final String INVITATION_SENT_LOG = "Staff invitation created: id={}, community={}, email={}, role={}";

	/**
	 * Actions the invitee must complete before the staff account is usable.
	 *
	 * <p>Invited staff are created without credentials, so UPDATE_PASSWORD is required in
	 * addition to email verification; UPDATE_PROFILE captures the missing profile details.
	 */
	private static final List<String> STAFF_INVITATION_REQUIRED_ACTIONS = List.of(
		"VERIFY_EMAIL",
		"UPDATE_PROFILE",
		"UPDATE_PASSWORD"
	);

	@Value("${app.invitations.expiry-days:7}")
	private int expiryDays;

	@Value("${app.invitations.redirect-uri:http://localhost:3000/staff-invitations}")
	private String invitationRedirectUri;

	private final StaffInvitationRepository invitationRepository;
	private final CommunityRepository communityRepository;
	private final UserRepository userRepository;
	private final UserCommunityRepository userCommunityRepository;
	private final UserCommunityStaffRoleRepository staffRoleRepository;
	private final KeycloakService keycloakService;
	private final SmsService smsService;
	private final TokenGenerator tokenGenerator;

	@Override
	@Transactional
	public StaffInvitationResponse createInvitation(UUID communityId, CreateStaffInvitationRequest request) {
		// 1. Verify community exists
		Community community = communityRepository.findByIdAndStatus(communityId, CommunityStatus.ACTIVE)
			.orElseThrow(() -> new NotFoundException("Community not found"));

		// 2. Check caller has STAFF_MANAGE permission
		// This is handled by @Permission annotation on controller, but we can add explicit check
		UUID inviterId = UUID.fromString(UserContextHolder.get().userId());

		// 3. Check for duplicate active pending invitation
		if (invitationRepository.findActivePendingByEmailAndCommunity(communityId, request.email()).isPresent()) {
			throw new ForbiddenException("A pending invitation already exists for this email in this community");
		}

		// @TODO Consider support assign staff role to existing user with invitation, if they are already a member of the community.
		// 4. Check existing active staff membership (if user exists)
		var existingUser = userRepository.findByEmail(request.email());
		if (existingUser.isPresent()) {
			if (userCommunityRepository.findByUserIdAndCommunityIdAndActiveTrue(
				existingUser.get().getId(),
				communityId).isPresent()) {
				throw new ForbiddenException("User is already an active member of this community");
			}
		}

		// 5. Create invitation with token and expiration (UTC)
		String token = tokenGenerator.generateSecureToken();
		LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
		LocalDateTime expiresAt = now.plusDays(expiryDays);

		StaffInvitation invitation = StaffInvitation.builder()
			.community(community)
			.firstName(request.firstName())
			.lastName(request.lastName())
			.email(request.email())
			.mobileNumber(request.mobileNumber())
			.position(request.position())
			.roleCode(request.roleCode())
			.token(token)
			.status(StaffInvitationStatus.PENDING)
			.invitedAt(now)
			.expiresAt(expiresAt)
			.invitedBy(userRepository.findById(inviterId).orElse(null))
			.notes(request.notes())
			.build();

		invitation = invitationRepository.save(invitation);
		log.info(INVITATION_SENT_LOG, invitation.getId(), communityId, request.email(), request.roleCode());

		// 6. Send invitation asynchronously (SMS and/or Email)
		sendInvitationAsync(invitation, token);

		return toResponse(invitation);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<StaffInvitationResponse> listInvitations(
		UUID communityId,
		StaffInvitationStatus status,
		String search,
		Pageable pageable
	) {
		// Verify community exists
		if (!communityRepository.existsById(communityId)) {
			throw new NotFoundException("Community not found");
		}

		Page<StaffInvitation> invitations = invitationRepository.findByFilters(
			communityId,
			status,
			search,
			pageable
		);

		return invitations.map(this::toResponse);
	}

	@Override
	@Transactional(readOnly = true)
	public StaffInvitationSummaryResponse getSummary(UUID communityId) {
		// Verify community exists
		if (!communityRepository.existsById(communityId)) {
			throw new NotFoundException("Community not found");
		}

		long total = invitationRepository.countByCommunityId(communityId);
		long pending = invitationRepository.countByCommunityIdAndStatus(communityId, StaffInvitationStatus.PENDING);
		long accepted = invitationRepository.countByCommunityIdAndStatus(communityId, StaffInvitationStatus.ACCEPTED);
		long expired = invitationRepository.countByCommunityIdAndStatus(communityId, StaffInvitationStatus.EXPIRED);
		long revoked = invitationRepository.countByCommunityIdAndStatus(communityId, StaffInvitationStatus.REVOKED);

		return new StaffInvitationSummaryResponse(total, pending, accepted, expired, revoked);
	}

	@Override
	@Transactional
	public int expireOldInvitations() {
		LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
		int count = invitationRepository.expireOldPending(now);
		if (count > 0) {
			log.info("Marked {} pending invitations as expired", count);
		}
		return count;
	}

	@Override
	@Transactional
	public StaffInvitationResponse revokeInvitation(UUID communityId, UUID invitationId, String reason) {
		StaffInvitation invitation = invitationRepository.findById(invitationId)
			.orElseThrow(() -> new NotFoundException("Invitation not found"));

		if (!invitation.getCommunityId().equals(communityId)) {
			throw new ForbiddenException("Invitation does not belong to this community");
		}

		if (invitation.getStatus() != StaffInvitationStatus.PENDING) {
			throw new IllegalStateException("Only pending invitations can be revoked");
		}

		invitation.setStatus(StaffInvitationStatus.REVOKED);
		invitation.setRevokedAt(LocalDateTime.now(ZoneId.of("UTC")));
		invitation.setNotes(reason);

		invitation = invitationRepository.save(invitation);
		log.info("Staff invitation revoked: id={}, community={}, reason={}", invitationId, communityId, reason);

		return toResponse(invitation);
	}

	@Override
	@Transactional
	public void activateInvitation(String token, UUID userId) {
		StaffInvitation invitation = invitationRepository.findByToken(token)
			.orElseThrow(() -> new NotFoundException("Invitation not found or token is invalid"));

		LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));

		if (invitation.getStatus() != StaffInvitationStatus.PENDING) {
			throw new IllegalStateException("Invitation is not in pending status");
		}

		if (invitation.getExpiresAt().isBefore(now)) {
			invitation.setStatus(StaffInvitationStatus.EXPIRED);
			invitationRepository.save(invitation);
			throw new IllegalStateException("Invitation has expired");
		}

		User user = userRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException("User not found"));

		// Ensure user is member of community
		UserCommunity membership = userCommunityRepository
			.findByUserIdAndCommunityId(userId, invitation.getCommunityId())
			.orElseGet(() -> UserCommunity.builder()
				.user(user)
				.community(invitation.getCommunity())
				.role(SilinganRealmRole.STAFF)
				.active(true)
				.build());

		userCommunityRepository.save(membership);

		// Assign staff role
		UserCommunityStaffRole assignment = staffRoleRepository
			.findByUserIdAndCommunityId(userId, invitation.getCommunityId())
			.orElseGet(() -> UserCommunityStaffRole.builder()
				.user(user)
				.community(invitation.getCommunity())
				.build());

		assignment.setRoleCode(invitation.getRoleCode());
		assignment.setActive(true);
		assignment.setAssignedAt(now);
		assignment.setAssignedBy(userRepository.findById(invitation.getInvitedByUserId()).orElse(null));
		staffRoleRepository.save(assignment);

		// Mark invitation as accepted
		invitation.setStatus(StaffInvitationStatus.ACCEPTED);
		invitation.setAcceptedAt(now);
		invitationRepository.save(invitation);

		log.info("Staff invitation activated: id={}, community={}, user={}, role={}",
			invitation.getId(), invitation.getCommunityId(), userId, invitation.getRoleCode());
	}

	@Override
	@Transactional(readOnly = true)
	public StaffInvitation getByToken(String token) {
		return invitationRepository.findByToken(token)
			.orElseThrow(() -> new NotFoundException("Invitation not found"));
	}

	/**
	 * Send invitation asynchronously via SMS and/or Email using Keycloak and SMS service.
	 * Uses @Async to avoid blocking the response.
	 */
	@Async
	protected void sendInvitationAsync(StaffInvitation invitation, String token) {
		try {
			String invitationLink = buildInvitationLink(token);
			
			// Send email via Keycloak
			if (invitation.getEmail() != null && !invitation.getEmail().isBlank()) {
				sendEmailViaKeycloak(invitation, invitationLink);
			}
			
			// Send SMS if mobile number provided
			if (invitation.getMobileNumber() != null && !invitation.getMobileNumber().isBlank()) {
				sendSmsViaService(invitation, invitationLink);
			}
			
			log.info("Invitation notifications sent: id={}, email={}, mobile={}", 
				invitation.getId(), invitation.getEmail(), invitation.getMobileNumber());
		} catch (Exception e) {
			log.error("Error sending invitation notifications for id={}", invitation.getId(), e);
			// Don't throw - invitation is already created, delivery failures should be logged but not block
		}
	}

	private void sendEmailViaKeycloak(StaffInvitation invitation, String invitationLink) {
		try {
			// 1. Create or get Keycloak user, seeding the name so the email can greet the invitee
			String keycloakUserId = keycloakService.findUserIdByEmail(invitation.getEmail())
				.orElseGet(() -> keycloakService.createInvitationUser(
					invitation.getEmail(), invitation.getFirstName(), invitation.getLastName()));

			// 2. Build the context the custom Keycloak email theme renders. StaffRoleCode is a
			//    Silingan application role, not a Keycloak realm role, so it travels to the theme
			//    as a user attribute rather than a role mapping.
			Community community = invitation.getCommunity();
			InvitationEmailContext context = InvitationEmailContext.staff(
				invitation.getRoleCode(),
				invitation.getCommunityId(),
				community != null ? community.getName() : null
			);

			// 3. Trigger Keycloak's executeActionsEmail() flow
			keycloakService.sendInvitationEmail(keycloakUserId, STAFF_INVITATION_REQUIRED_ACTIONS, context);

			log.info("Email invitation sent via Keycloak: email={}, keycloakUserId={}, roleCode={}",
				invitation.getEmail(), keycloakUserId, invitation.getRoleCode());
		} catch (Exception e) {
			log.error("Failed to send email invitation via Keycloak for {}", invitation.getEmail(), e);
			// Continue to SMS sending if email fails
		}
	}

	private void sendSmsViaService(StaffInvitation invitation, String invitationLink) {
		try {
			String message = buildInvitationSmsMessage(invitation, invitationLink);
			smsService.sendSms(invitation.getMobileNumber(), message);
			
			log.info("SMS invitation sent: mobile={}, invitationId={}", 
				invitation.getMobileNumber(), invitation.getId());
		} catch (Exception e) {
			log.error("Failed to send SMS invitation to {}", invitation.getMobileNumber(), e);
		}
	}

	private String buildInvitationLink(String token) {
		return invitationRedirectUri + "?token=" + token;
	}

	private String buildInvitationSmsMessage(StaffInvitation invitation, String invitationLink) {
		return String.format(
			"Hello %s,\n\n" +
			"You have been invited to join %s as a %s staff member.\n\n" +
			"Accept invitation: %s\n\n" +
			"This invitation expires in 7 days.",
			invitation.getFirstName(),
			invitation.getCommunity().getName(),
			invitation.getRoleCode().displayName(),
			invitationLink
		);
	}

	private StaffInvitationResponse toResponse(StaffInvitation invitation) {
		return new StaffInvitationResponse(
			invitation.getId(),
			invitation.getCommunityId(),
			invitation.getFirstName(),
			invitation.getLastName(),
			invitation.getEmail(),
			invitation.getMobileNumber(),
			invitation.getPosition(),
			invitation.getRoleCode(),
			invitation.getStatus(),
			invitation.getInvitedAt(),
			invitation.getExpiresAt(),
			invitation.getAcceptedAt(),
			invitation.getRevokedAt(),
			invitation.getInvitedBy() != null ? invitation.getInvitedBy().getFirstName() + " " + invitation.getInvitedBy().getLastName() : null,
			invitation.getNotes()
		);
	}
}
