package com.ria.olita.tech.silingan.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ria.olita.tech.silingan.dto.req.CreateStaffInvitationRequest;
import com.ria.olita.tech.silingan.dto.res.StaffInvitationResponse;
import com.ria.olita.tech.silingan.dto.res.StaffInvitationSummaryResponse;
import com.ria.olita.tech.silingan.entity.StaffInvitation;
import com.ria.olita.tech.silingan.entity.StaffInvitationStatus;

public interface StaffInvitationService {

	/**
	 * Create a new staff invitation for a community.
	 *
	 * @throws com.ria.olita.tech.silingan.exception.ForbiddenException if caller lacks STAFF_MANAGE
	 * @throws com.ria.olita.tech.silingan.exception.ForbiddenException if duplicate pending invitation exists
	 * @throws com.ria.olita.tech.silingan.exception.ForbiddenException if active staff membership exists
	 * @throws com.ria.olita.tech.silingan.exception.NotFoundException if community not found
	 */
	StaffInvitationResponse createInvitation(UUID communityId, CreateStaffInvitationRequest request);

	/**
	 * List invitations for a community with optional filtering and search.
	 *
	 * @param communityId the community to query
	 * @param status optional filter by status (null = all)
	 * @param search optional search term (searches firstName, lastName, email, position)
	 * @param pageable pagination details
	 * @return paginated invitations (token never included)
	 */
	Page<StaffInvitationResponse> listInvitations(
		UUID communityId,
		StaffInvitationStatus status,
		String search,
		Pageable pageable
	);

	/**
	 * Get summary counts for invitations in a community.
	 *
	 * @return total, pending, accepted, expired, revoked counts
	 */
	StaffInvitationSummaryResponse getSummary(UUID communityId);

	/**
	 * Expire all pending invitations that have passed their expiration time.
	 * Called by scheduled task. Idempotent: safe to call multiple times.
	 *
	 * @return count of invitations marked as EXPIRED
	 */
	int expireOldInvitations();

	/**
	 * Revoke an active invitation (admin action).
	 *
	 * @throws com.ria.olita.tech.silingan.exception.ForbiddenException if not STAFF_MANAGE
	 * @throws com.ria.olita.tech.silingan.exception.NotFoundException if invitation not found
	 * @throws IllegalStateException if invitation is not in PENDING status
	 */
	StaffInvitationResponse revokeInvitation(UUID communityId, UUID invitationId, String reason);

	/**
	 * Activate an invitation using its token.
	 * Called after user completes verification flow.
	 *
	 * @param token the invitation token
	 * @param userId the user to assign the staff role to
	 * @throws com.ria.olita.tech.silingan.exception.NotFoundException if token/invitation not found
	 * @throws IllegalStateException if invitation is expired or already accepted
	 */
	void activateInvitation(String token, UUID userId);

	/**
	 * Get invitation details by token (used internally, for UI token is never exposed).
	 *
	 * @throws com.ria.olita.tech.silingan.exception.NotFoundException if not found
	 */
	StaffInvitation getByToken(String token);
}
