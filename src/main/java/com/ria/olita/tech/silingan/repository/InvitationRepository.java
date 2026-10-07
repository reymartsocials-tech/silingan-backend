package com.ria.olita.tech.silingan.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ria.olita.tech.silingan.entity.Invitation;
import com.ria.olita.tech.silingan.entity.InvitationStatus;
import com.ria.olita.tech.silingan.entity.InvitationType;

@Repository
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

	// ===== STAFF INVITATION QUERIES =====

	/**
	 * Find active pending staff invitations by email and community.
	 */
	@Query("SELECT i FROM Invitation i WHERE i.communityId = :communityId " +
		"AND i.type = :type " +
		"AND LOWER(i.email) = LOWER(:email) " +
		"AND i.status = :status " +
		"AND i.expiresAt > :now")
	Optional<Invitation> findActivePendingByEmailAndCommunity(
		@Param("communityId") UUID communityId,
		@Param("type") InvitationType type,
		@Param("email") String email,
		@Param("status") InvitationStatus status,
		@Param("now") LocalDateTime now
	);

	/**
	 * Check if pending staff invitations exist for an email in a community (supports multiple identities).
	 */
	@Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Invitation i " +
		"WHERE i.communityId = :communityId " +
		"AND i.type = :type " +
		"AND LOWER(i.email) = LOWER(:email) " +
		"AND i.status = :status " +
		"AND i.expiresAt > :now")
	boolean hasActivePendingByEmail(
		@Param("communityId") UUID communityId,
		@Param("type") InvitationType type,
		@Param("email") String email,
		@Param("status") InvitationStatus status,
		@Param("now") LocalDateTime now
	);

	/**
	 * Find pending staff invitations by Keycloak user ID (used during login activation).
	 */
	@Query("SELECT i FROM Invitation i WHERE i.keycloakUserId = :keycloakUserId " +
		"AND i.type = :type " +
		"AND i.status = :status " +
		"AND i.expiresAt > :now")
	Optional<Invitation> findPendingByKeycloakUserIdAndType(
		@Param("keycloakUserId") String keycloakUserId,
		@Param("type") InvitationType type,
		@Param("status") InvitationStatus status,
		@Param("now") LocalDateTime now
	);

	/**
	 * Find any pending invitation for a user (STAFF or ADMIN).
	 * Used by the activation filter to extract communityId from invitation.
	 *
	 * @param keycloakUserId the Keycloak user ID
	 * @param status invitation status (typically PENDING)
	 * @param now current timestamp for expiration check
	 * @return first pending invitation if exists, empty otherwise
	 */
	@Query("SELECT i FROM Invitation i WHERE i.keycloakUserId = :keycloakUserId " +
		"AND i.status = :status " +
		"AND i.expiresAt > :now " +
		"ORDER BY i.invitedAt DESC " +
		"LIMIT 1")
	Optional<Invitation> findAnyPendingInvitation(
		@Param("keycloakUserId") String keycloakUserId,
		@Param("status") InvitationStatus status,
		@Param("now") LocalDateTime now
	);

	// ===== ADMIN INVITATION QUERIES =====

	/**
	 * Check if a community has an active pending admin invitation.
	 */
	@Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Invitation i " +
		"WHERE i.communityId = :communityId " +
		"AND i.type = :type " +
		"AND i.status = :status " +
		"AND i.expiresAt > :now")
	boolean hasActivePendingInvitation(
		@Param("communityId") UUID communityId,
		@Param("type") InvitationType type,
		@Param("status") InvitationStatus status,
		@Param("now") LocalDateTime now
	);

	// ===== EXPIRATION MANAGEMENT =====

	/**
	 * Mark stale pending invitations as expired.
	 */
	@Modifying
	@Query("UPDATE Invitation i SET i.status = :expiredStatus " +
		"WHERE i.communityId = :communityId " +
		"AND i.type = :type " +
		"AND i.status = :pendingStatus " +
		"AND i.expiresAt <= :now")
	void expireStalePendingForCommunityAndType(
		@Param("communityId") UUID communityId,
		@Param("type") InvitationType type,
		@Param("pendingStatus") InvitationStatus pendingStatus,
		@Param("expiredStatus") InvitationStatus expiredStatus,
		@Param("now") LocalDateTime now
	);

	/**
	 * Mark stale pending staff invitations for an email as expired.
	 */
	@Modifying
	@Query("UPDATE Invitation i SET i.status = :expiredStatus " +
		"WHERE i.communityId = :communityId " +
		"AND i.type = :type " +
		"AND LOWER(i.email) = LOWER(:email) " +
		"AND i.status = :pendingStatus " +
		"AND i.expiresAt <= :now")
	void expireStalePendingForEmailAndType(
		@Param("communityId") UUID communityId,
		@Param("type") InvitationType type,
		@Param("email") String email,
		@Param("pendingStatus") InvitationStatus pendingStatus,
		@Param("expiredStatus") InvitationStatus expiredStatus,
		@Param("now") LocalDateTime now
	);

	// ===== PAGINATION QUERIES =====

	/**
	 * List invitations for a community without type filter.
	 */
	Page<Invitation> findByCommunityId(
		UUID communityId,
		Pageable pageable
	);

	/**
	 * List invitations for a community with status filter.
	 */
	Page<Invitation> findByCommunityIdAndStatus(
		UUID communityId,
		InvitationStatus status,
		Pageable pageable
	);

	/**
	 * List admin invitations for a community with filters.
	 */
	Page<Invitation> findByCommunityIdAndType(
		UUID communityId,
		InvitationType type,
		Pageable pageable
	);

	/**
	 * List admin invitations for a community with status filter.
	 */
	Page<Invitation> findByCommunityIdAndTypeAndStatus(
		UUID communityId,
		InvitationType type,
		InvitationStatus status,
		Pageable pageable
	);

	// ===== ACTIVATION QUERIES =====

	/**
	 * Find active pending admin invitation for a Keycloak user in a community.
	 */
	@Query("SELECT i FROM Invitation i WHERE i.keycloakUserId = :keycloakUserId " +
		"AND i.communityId = :communityId " +
		"AND i.type = 'ADMIN' " +
		"AND i.status = :status " +
		"AND i.expiresAt > :now")
	Optional<Invitation> findActivePendingAdminInvitation(
		@Param("keycloakUserId") String keycloakUserId,
		@Param("communityId") UUID communityId,
		@Param("status") InvitationStatus status,
		@Param("now") LocalDateTime now
	);

	// ===== COUNTING QUERIES =====

	/**
	 * Count total invitations for a community.
	 */
	long countByCommunityId(UUID communityId);

	/**
	 * Count invitations for a community by status.
	 */
	long countByCommunityIdAndStatus(UUID communityId, InvitationStatus status);

	/**
	 * Marks all pending invitations that have expired as EXPIRED.
	 * Runs periodically to clean up stale invitations that were never accepted or explicitly revoked.
	 */
	@Modifying
	@Query("UPDATE Invitation i SET i.status = :expiredStatus " +
		"WHERE i.status = :pendingStatus AND i.expiresAt <= :now")
	int expireOldPendingInvitations(
		@Param("pendingStatus") InvitationStatus pendingStatus,
		@Param("expiredStatus") InvitationStatus expiredStatus,
		@Param("now") LocalDateTime now
	);
}
