package com.ria.olita.tech.silingan.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ria.olita.tech.silingan.entity.CommunityJoinInvitation;
import com.ria.olita.tech.silingan.entity.CommunityJoinInvitationStatus;

@Repository
public interface CommunityJoinInvitationRepository extends JpaRepository<CommunityJoinInvitation, UUID> {

	/** Lookup is always by hash; the raw token is never a query parameter. */
	Optional<CommunityJoinInvitation> findByTokenHash(String tokenHash);

	boolean existsByTokenHash(String tokenHash);

	Optional<CommunityJoinInvitation> findByIdAndCommunityId(UUID id, UUID communityId);

	Page<CommunityJoinInvitation> findByCommunityIdOrderByCreatedAtDescIdDesc(UUID communityId, Pageable pageable);

	/**
	 * Atomically consumes one use. The guard conditions live in the {@code WHERE} clause, so two
	 * requests competing for the last available use can never both succeed: exactly one of them
	 * updates a row and the other sees {@code 0}.
	 *
	 * @return number of rows updated: 1 when the use was consumed, 0 when the invitation was
	 * concurrently revoked, expired or exhausted.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		UPDATE CommunityJoinInvitation i
		SET i.usedCount = i.usedCount + 1,
			i.lastUsedAt = :now,
			i.status = CASE
				WHEN i.maxUses IS NOT NULL AND i.usedCount + 1 >= i.maxUses THEN :exhaustedStatus
				ELSE i.status
			END
		WHERE i.id = :invitationId
			AND i.status = :activeStatus
			AND i.expiresAt > :now
			AND (i.maxUses IS NULL OR i.usedCount < i.maxUses)
		""")
	int consumeOneUse(
		@Param("invitationId") UUID invitationId,
		@Param("now") LocalDateTime now,
		@Param("activeStatus") CommunityJoinInvitationStatus activeStatus,
		@Param("exhaustedStatus") CommunityJoinInvitationStatus exhaustedStatus
	);
}
