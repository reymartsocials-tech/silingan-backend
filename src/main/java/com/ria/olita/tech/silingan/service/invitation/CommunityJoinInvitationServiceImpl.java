package com.ria.olita.tech.silingan.service.invitation;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.config.OnboardingProperties;
import com.ria.olita.tech.silingan.dto.req.CreateCommunityJoinInvitationRequest;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinInvitationCreatedResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinInvitationPreviewResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinInvitationResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityResponse;
import com.ria.olita.tech.silingan.dto.res.QrCodeImageResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityJoinInvitation;
import com.ria.olita.tech.silingan.entity.CommunityJoinInvitationStatus;
import com.ria.olita.tech.silingan.entity.CommunityJoinInvitationType;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.exception.ConflictException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.repository.CommunityJoinInvitationRepository;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.security.scope.CommunityScopeGuard;
import com.ria.olita.tech.silingan.service.CommunityService;
import com.ria.olita.tech.silingan.service.qr.CommunityJoinUrlFactory;
import com.ria.olita.tech.silingan.service.qr.QrCodeGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates, lists, revokes and validates community join invitations.
 *
 * <p>The raw token exists only inside {@link #create}: everywhere else the invitation is found by
 * the SHA-256 hash of the submitted token. No method logs, returns or stores a raw token.
 *
 * <p>Token validation never decides on its own whether a community may accept residents; it
 * delegates to {@link CommunityService#getByCode(String)}, the same check manual community-code
 * entry performs, so both onboarding entry points can never drift apart.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityJoinInvitationServiceImpl implements CommunityJoinInvitationService {

	/**
	 * Deliberately uniform: an unauthenticated caller must not be able to tell an unknown token
	 * from a revoked, expired or exhausted one, nor probe which communities exist.
	 */
	static final String INVALID_INVITATION_MESSAGE =
		"This invitation is invalid or no longer available. Please ask your community administrator for a new one.";

	private static final Pattern TOKEN_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{32,128}$");
	private static final Pattern NON_FILENAME_CHARS = Pattern.compile("[^a-z0-9]+");
	private static final int FILENAME_NAME_MAX = 40;
	private static final String PNG_CONTENT_TYPE = "image/png";

	private final CommunityJoinInvitationRepository invitationRepository;
	private final CommunityRepository communityRepository;
	private final UserRepository userRepository;
	private final CommunityService communityService;
	private final CommunityScopeGuard communityScopeGuard;
	private final CommunityJoinUrlFactory communityJoinUrlFactory;
	private final QrCodeGenerator qrCodeGenerator;
	private final SecureInvitationTokenGenerator tokenGenerator;
	private final InvitationTokenHasher tokenHasher;
	private final OnboardingProperties onboardingProperties;
	private final Clock clock;

	@Override
	@Transactional
	public CommunityJoinInvitationCreatedResponse create(UUID communityId, CreateCommunityJoinInvitationRequest request) {
		Community community = resolveManageableCommunity(communityId);

		OnboardingProperties.Invitation limits = onboardingProperties.getInvitation();
		int expiresInHours = resolveExpiryHours(request.expiresInHours(), limits);
		int maxUses = resolveMaxUses(request.maxUses(), limits);

		String rawToken = tokenGenerator.generate();

		CommunityJoinInvitation invitation = CommunityJoinInvitation.builder()
			.community(community)
			.tokenHash(tokenHasher.hash(rawToken))
			.invitationType(CommunityJoinInvitationType.COMMUNITY_BATCH)
			.status(CommunityJoinInvitationStatus.ACTIVE)
			.label(trimToNull(request.label()))
			.distributionChannel(request.distributionChannel())
			.expiresAt(LocalDateTime.now(clock).plusHours(expiresInHours))
			.maxUses(maxUses)
			.usedCount(0)
			.build();

		invitationRepository.saveAndFlush(invitation);

		URI joinUrl = communityJoinUrlFactory.create(rawToken);
		byte[] png = qrCodeGenerator.generatePng(joinUrl.toString());

		log.info(
			"Community join invitation {} created for community {} (expiresInHours={}, maxUses={})",
			invitation.getId(), community.getId(), expiresInHours, maxUses
		);

		return CommunityJoinInvitationCreatedResponse.builder()
			.invitation(toResponse(invitation, community))
			.joinUrl(joinUrl.toString())
			.qrCode(QrCodeImageResponse.builder()
				.contentType(PNG_CONTENT_TYPE)
				.fileName(toFileName(community.getName()))
				.base64(Base64.getEncoder().encodeToString(png))
				.build())
			.build();
	}

	@Override
	@Transactional(readOnly = true)
	public Page<CommunityJoinInvitationResponse> list(UUID communityId, Pageable pageable) {
		Community community = resolveManageableCommunity(communityId);

		return invitationRepository
			.findByCommunityIdOrderByCreatedAtDescIdDesc(community.getId(), pageable)
			.map(invitation -> toResponse(invitation, community));
	}

	@Override
	@Transactional
	public CommunityJoinInvitationResponse revoke(UUID communityId, UUID invitationId) {
		Community community = resolveManageableCommunity(communityId);

		if (invitationId == null) {
			throw new ValidationException("Invitation id is required");
		}

		// Scoped by community so a Community Admin cannot revoke another community's invitation by
		// guessing an id.
		CommunityJoinInvitation invitation = invitationRepository
			.findByIdAndCommunityId(invitationId, community.getId())
			.orElseThrow(() -> new NotFoundException("Join invitation not found with id = " + invitationId));

		if (invitation.getStatus() != CommunityJoinInvitationStatus.REVOKED) {
			invitation.setStatus(CommunityJoinInvitationStatus.REVOKED);
			invitation.setRevokedAt(LocalDateTime.now(clock));
			invitation.setRevokedBy(currentUserReference());
			invitationRepository.saveAndFlush(invitation);

			log.info("Community join invitation {} revoked for community {}", invitation.getId(), community.getId());
		}

		return toResponse(invitation, community);
	}

	@Override
	@Transactional(readOnly = true)
	public CommunityJoinInvitationPreviewResponse preview(String rawToken) {
		CommunityJoinInvitation invitation = requireUsableInvitation(rawToken);
		Community community = invitation.getCommunity();

		// Reuses the manual community-code eligibility check, so a QR can never onboard into a
		// community that manual entry would reject.
		requireEligibleCommunity(community);

		return CommunityJoinInvitationPreviewResponse.builder()
			.invitationId(invitation.getId())
			.communityId(community.getId())
			.communityName(community.getName())
			.communityLogoUrl(community.getLogo() != null ? community.getLogo().getUrl() : null)
			.expiresAt(invitation.getExpiresAt())
			.build();
	}

	@Override
	@Transactional(readOnly = true)
	public CommunityJoinInvitationResolution resolveForRegistration(String rawToken) {
		CommunityJoinInvitation invitation = requireUsableInvitation(rawToken);
		CommunityResponse community = requireEligibleCommunity(invitation.getCommunity());

		return new CommunityJoinInvitationResolution(invitation.getId(), community);
	}

	@Override
	@Transactional
	public void consumeUse(UUID invitationId) {
		int updated = invitationRepository.consumeOneUse(
			invitationId,
			LocalDateTime.now(clock),
			CommunityJoinInvitationStatus.ACTIVE,
			CommunityJoinInvitationStatus.EXHAUSTED
		);

		if (updated == 0) {
			// The invitation was revoked, expired or exhausted between validation and this update;
			// the surrounding transaction is rolled back so no membership is created.
			log.warn("Community join invitation {} could not be consumed", invitationId);
			throw new ConflictException(INVALID_INVITATION_MESSAGE);
		}

		log.info("Community join invitation {} consumed", invitationId);
	}

	/**
	 * Resolves a raw token to a currently usable invitation. All failure modes raise the same
	 * message so the endpoint cannot be used as an oracle.
	 */
	private CommunityJoinInvitation requireUsableInvitation(String rawToken) {
		String token = rawToken == null ? "" : rawToken.trim();

		if (!TOKEN_FORMAT.matcher(token).matches()) {
			throw new NotFoundException(INVALID_INVITATION_MESSAGE);
		}

		CommunityJoinInvitation invitation = invitationRepository.findByTokenHash(tokenHasher.hash(token))
			.orElseThrow(() -> new NotFoundException(INVALID_INVITATION_MESSAGE));

		if (Boolean.TRUE.equals(invitation.getDeleted()) || !invitation.isUsable(LocalDateTime.now(clock))) {
			throw new NotFoundException(INVALID_INVITATION_MESSAGE);
		}

		return invitation;
	}

	/**
	 * Delegates to the existing public community-code resolver so invitation onboarding and manual
	 * onboarding share one definition of an eligible community.
	 */
	private CommunityResponse requireEligibleCommunity(Community community) {
		if (community == null || community.getCommunityCode() == null || community.getCommunityCode().isBlank()) {
			throw new NotFoundException(INVALID_INVITATION_MESSAGE);
		}

		try {
			return communityService.getByCode(community.getCommunityCode());
		} catch (NotFoundException ex) {
			throw new NotFoundException(INVALID_INVITATION_MESSAGE);
		}
	}

	/**
	 * Resolves the community the caller may manage onboarding for. The path community is checked
	 * against the caller's actual access so an admin of one community cannot mint join credentials
	 * for another.
	 */
	private Community resolveManageableCommunity(UUID communityId) {
		if (communityId == null) {
			throw new ValidationException("Community id is required");
		}

		communityScopeGuard.assertAccess(communityId);

		Community community = communityRepository.findById(communityId)
			.orElseThrow(() -> new NotFoundException("Community not found with id = " + communityId));

		if (community.getStatus() != CommunityStatus.ACTIVE) {
			throw new ConflictException("Community is not accepting new residents");
		}

		if (community.getCommunityCode() == null || community.getCommunityCode().isBlank()) {
			throw new ConflictException("Community does not have a join code");
		}

		return community;
	}

	private int resolveExpiryHours(Integer requested, OnboardingProperties.Invitation limits) {
		if (requested == null) {
			return limits.getDefaultExpiryHours();
		}
		if (requested < limits.getMinExpiryHours() || requested > limits.getMaxExpiryHours()) {
			throw new ValidationException(
				"Expiry must be between %d and %d hours".formatted(limits.getMinExpiryHours(), limits.getMaxExpiryHours())
			);
		}
		return requested;
	}

	private int resolveMaxUses(Integer requested, OnboardingProperties.Invitation limits) {
		if (requested == null) {
			return limits.getDefaultMaxUses();
		}
		if (requested < limits.getMinMaxUses() || requested > limits.getMaxMaxUses()) {
			throw new ValidationException(
				"Maximum uses must be between %d and %d".formatted(limits.getMinMaxUses(), limits.getMaxMaxUses())
			);
		}
		return requested;
	}

	private CommunityJoinInvitationResponse toResponse(CommunityJoinInvitation invitation, Community community) {
		return CommunityJoinInvitationResponse.builder()
			.invitationId(invitation.getId())
			.communityId(community.getId())
			.communityName(community.getName())
			.type(invitation.getInvitationType())
			.label(invitation.getLabel())
			.distributionChannel(invitation.getDistributionChannel())
			.status(invitation.effectiveStatus(LocalDateTime.now(clock)))
			.createdByName(displayName(invitation.getCreatedBy()))
			.createdAt(invitation.getCreatedAt())
			.expiresAt(invitation.getExpiresAt())
			.maxUses(invitation.getMaxUses())
			.usedCount(invitation.getUsedCount())
			.lastUsedAt(invitation.getLastUsedAt())
			.revokedByName(displayName(invitation.getRevokedBy()))
			.revokedAt(invitation.getRevokedAt())
			.build();
	}

	private String displayName(User user) {
		if (user == null) {
			return null;
		}

		String name = "%s %s".formatted(
			Optional.ofNullable(user.getFirstName()).orElse(""),
			Optional.ofNullable(user.getLastName()).orElse("")
		).trim();

		return name.isBlank() ? null : name;
	}

	private User currentUserReference() {
		UserContext context = UserContextHolder.get();
		if (context == null || context.userId() == null || context.userId().isBlank()) {
			return null;
		}

		return userRepository.findById(UUID.fromString(context.userId())).orElse(null);
	}

	private String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private String toFileName(String communityName) {
		String slug = NON_FILENAME_CHARS
			.matcher(communityName == null ? "" : communityName.toLowerCase(Locale.ROOT))
			.replaceAll("-")
			.replaceAll("^-|-$", "");

		if (slug.isBlank()) {
			slug = "community";
		} else if (slug.length() > FILENAME_NAME_MAX) {
			slug = slug.substring(0, FILENAME_NAME_MAX)
				.replaceAll("-$", "");
		}

		return "silingan-%s-join.png".formatted(slug);
	}
}
