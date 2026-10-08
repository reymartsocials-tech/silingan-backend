package com.ria.olita.tech.silingan.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ria.olita.tech.silingan.dto.req.InvitationRequest;
import com.ria.olita.tech.silingan.dto.res.InvitationSummaryResponse;
import com.ria.olita.tech.silingan.dto.res.InvitationStatsResponse;
import com.ria.olita.tech.silingan.entity.InvitationStatus;

public interface InviteService {
	
	/**
	 * Create a new invitation (staff or admin).
	 * For admin invitations, roleCode must be "COMMUNITY_ADMIN".
	 * For staff invitations, all fields including firstName, lastName, position, roleCode are required.
	 */
	InvitationSummaryResponse invite(UUID communityId, InvitationRequest request);
	
	/**
	 * List all invitations for a community with optional filtering and pagination.
	 */
	Page<InvitationSummaryResponse> listInvitations(UUID communityId, InvitationStatus status, String search, Pageable pageable);
	
	/**
	 * Get invitation statistics (total, pending, accepted, expired, revoked).
	 */
	InvitationStatsResponse getSummary(UUID communityId);
	
	/**
	 * Revoke a pending invitation.
	 */
	InvitationSummaryResponse revokeInvitation(UUID communityId, UUID invitationId, String reason);
	
	/**
	 * Activate an invitation upon user onboarding completion.
	 * Called by UserContextFilter when required Keycloak actions are complete.
	 * 
	 * @return true if any invitation was activated, false if no activations occurred
	 */
	boolean activateInvitation(String keycloakUserId, UUID communityId, String email);
}
