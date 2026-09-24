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

import com.ria.olita.tech.silingan.entity.StaffInvitation;
import com.ria.olita.tech.silingan.entity.StaffInvitationStatus;

@Repository
public interface StaffInvitationRepository extends JpaRepository<StaffInvitation, UUID> {

	Optional<StaffInvitation> findByToken(String token);

	/**
	 * Find active pending invitation for a community and email.
	 * Uses expiration check inline: pending status + expires_at > now.
	 */
	@Query("""
		SELECT i
		FROM StaffInvitation i
		WHERE i.communityId = :communityId
		  AND i.email = :email
		  AND i.status = 'PENDING'
		  AND i.expiresAt > CURRENT_TIMESTAMP
		LIMIT 1
	""")
	Optional<StaffInvitation> findActivePendingByEmailAndCommunity(
		@Param("communityId") UUID communityId,
		@Param("email") String email
	);

	/**
	 * Count total invitations for a community (all statuses).
	 */
	long countByCommunityId(UUID communityId);

	/**
	 * Count invitations by status for a community.
	 */
	long countByCommunityIdAndStatus(UUID communityId, StaffInvitationStatus status);

	/**
	 * List invitations with pagination and filters.
	 */
	@Query("""
		SELECT i
		FROM StaffInvitation i
		WHERE i.communityId = :communityId
		  AND (:status IS NULL OR i.status = :status)
		  AND (
		    :search IS NULL OR
		    LOWER(i.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR
		    LOWER(i.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR
		    LOWER(i.email) LIKE LOWER(CONCAT('%', :search, '%')) OR
		    LOWER(i.position) LIKE LOWER(CONCAT('%', :search, '%'))
		  )
		ORDER BY i.invitedAt DESC
	""")
	Page<StaffInvitation> findByFilters(
		@Param("communityId") UUID communityId,
		@Param("status") StaffInvitationStatus status,
		@Param("search") String search,
		Pageable pageable
	);

	/**
	 * Find all pending invitations that have expired.
	 * Used by scheduled task to mark as EXPIRED.
	 */
	@Query("""
		SELECT i
		FROM StaffInvitation i
		WHERE i.status = 'PENDING'
		  AND i.expiresAt <= :now
	""")
	List<StaffInvitation> findExpiredPending(@Param("now") LocalDateTime now);

	/**
	 * Mark all expired pending invitations in bulk.
	 * Idempotent: only updates those still in PENDING status.
	 */
	@Modifying
	@Query("""
		UPDATE StaffInvitation i
		SET i.status = 'EXPIRED'
		WHERE i.status = 'PENDING'
		  AND i.expiresAt <= :now
	""")
	int expireOldPending(@Param("now") LocalDateTime now);
}
