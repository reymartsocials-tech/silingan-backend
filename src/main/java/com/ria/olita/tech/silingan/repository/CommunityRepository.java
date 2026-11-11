package com.ria.olita.tech.silingan.repository;

import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.CommunityType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public interface CommunityRepository extends JpaRepository<Community, UUID> {

	@Query("Select c from Community c where c.communityCode = ?1 AND c.status = ?2")
	Optional<Community> findByCodeAndStatus(String code, CommunityStatus status);

	@Query("Select c from Community c where c.id = ?1 AND c.status = ?2")
	Optional<Community> findByIdAndStatus(UUID id, CommunityStatus status);

	boolean existsByIdAndStatus(UUID id, CommunityStatus status);

	@Query("select CASE WHEN COUNT(c) > 0 THEN TRUE ELSE FALSE END FROM Community c WHERE c.communityCode = ?1 AND c.status = ?2")
	boolean existsByCodeAndStatus(String code, CommunityStatus status);

	List<Community> findByStatus(CommunityStatus status);

	List<Community> findByType(CommunityType type);


	@Query("SELECT c FROM Community c WHERE (LOWER(c.name) LIKE LOWER(CONCAT('%', ?1, '%')) OR LOWER(c.communityCode) LIKE LOWER(CONCAT('%', ?2, '%'))) AND c.status = ?3")
	List<Community> searchByCommunityNameOrCodeAndStatus(String name, String code, CommunityStatus status);

	@Query("""
		SELECT c.id, c.name
		FROM UserCommunity uc
		JOIN uc.community c
		JOIN uc.user u
		WHERE uc.user.id = :userId
			AND c.status = 'ACTIVE'
			AND u.status = 'ACTIVE'
		""")
	List<Object[]> findActiveCommunityIdNamePairsByUserId(UUID userId);

	default Map<UUID, String> findActiveCommunityNameMapByUserId(UUID userId) {
		return findActiveCommunityIdNamePairsByUserId(userId).stream()
			.collect(Collectors.toMap(
				row -> (UUID) row[0],
				row -> (String) row[1],
				(left, right) -> left
			));
	}

	@Modifying
	@Query("UPDATE Community c SET c.deleted = true, c.deletedAt = :at WHERE c.id = :id")
	int softDelete(@Param("id") UUID id, @Param("at") LocalDateTime at);

	/**
	 * Communities that existed at {@code at}: created by then and not yet soft-deleted. Native, because
	 * the entity's {@code @SQLRestriction} would hide the since-deleted rows that were still live then.
	 */
	@Query(value = "SELECT COUNT(*) FROM communities WHERE created_at <= :at " +
		"AND (deleted = false OR deleted_at > :at)", nativeQuery = true)
	long countExistingAt(@Param("at") LocalDateTime at);

	/** Soft-deleted rows without a deletion time (no history), which make a past total unreliable. */
	@Query(value = "SELECT COUNT(*) FROM communities WHERE deleted = true AND deleted_at IS NULL", nativeQuery = true)
	long countDeletedWithoutTimestamp();

	/** One grouped query; soft-deleted communities are excluded by the entity's {@code @SQLRestriction}. */
	@Query("SELECT new com.ria.olita.tech.silingan.repository.CommunityStatusCount(c.status, COUNT(c), " +
		"SUM(CASE WHEN EXISTS (SELECT 1 FROM UserCommunity uc JOIN uc.user u WHERE uc.community = c " +
		"AND uc.role = com.ria.olita.tech.silingan.entity.SilinganRealmRole.COMMUNITY_ADMIN " +
		"AND u.status = com.ria.olita.tech.silingan.entity.UserStatus.ACTIVE) THEN 1 ELSE 0 END)) " +
		"FROM Community c GROUP BY c.status")
	List<CommunityStatusCount> countByStatusWithAdmin();

	/** Communities created per calendar month within {@code [from, to)}; empty months are not returned. */
	@Query("SELECT new com.ria.olita.tech.silingan.repository.CommunityMonthlyCount(" +
		"YEAR(c.createdAt), MONTH(c.createdAt), COUNT(c)) " +
		"FROM Community c WHERE c.createdAt >= :from AND c.createdAt < :to " +
		"GROUP BY YEAR(c.createdAt), MONTH(c.createdAt)")
	List<CommunityMonthlyCount> countCreatedPerMonth(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	@Query("SELECT new com.ria.olita.tech.silingan.repository.PlatformActivityRow(c.id, c.id, c.name, c.createdAt) " +
		"FROM Community c WHERE c.createdAt IS NOT NULL ORDER BY c.createdAt DESC, c.id DESC")
	List<PlatformActivityRow> findRecentlyCreated(Pageable pageable);

	/**
	 * Communities that are not active, have no active administrator, or have a pending administrator
	 * invitation, with their counts, in one bounded statement. Severity order: no administrator,
	 * then not active, then invitation-only; name and id break ties.
	 */
	@Query("SELECT new com.ria.olita.tech.silingan.repository.AttentionCommunityRow(c.id, c.name, c.status, " +
		"(SELECT COUNT(r) FROM UserCommunity r JOIN r.user ru WHERE r.community = c " +
		"AND r.role = com.ria.olita.tech.silingan.entity.SilinganRealmRole.RESIDENT " +
		"AND ru.status = com.ria.olita.tech.silingan.entity.UserStatus.ACTIVE), " +
		"(SELECT COUNT(a) FROM UserCommunity a JOIN a.user au WHERE a.community = c " +
		"AND a.role = com.ria.olita.tech.silingan.entity.SilinganRealmRole.COMMUNITY_ADMIN " +
		"AND au.status = com.ria.olita.tech.silingan.entity.UserStatus.ACTIVE), " +
		"(SELECT COUNT(p) FROM Invitation p WHERE p.communityId = c.id " +
		"AND p.type = com.ria.olita.tech.silingan.entity.InvitationType.ADMIN " +
		"AND p.status = com.ria.olita.tech.silingan.entity.InvitationStatus.PENDING AND p.expiresAt > :now)) " +
		"FROM Community c WHERE c.status <> com.ria.olita.tech.silingan.entity.CommunityStatus.ACTIVE " +
		"OR NOT EXISTS (SELECT 1 FROM UserCommunity uc JOIN uc.user u WHERE uc.community = c " +
		"AND uc.role = com.ria.olita.tech.silingan.entity.SilinganRealmRole.COMMUNITY_ADMIN " +
		"AND u.status = com.ria.olita.tech.silingan.entity.UserStatus.ACTIVE) OR EXISTS (SELECT 1 FROM Invitation i WHERE i.communityId = c.id " +
		"AND i.type = com.ria.olita.tech.silingan.entity.InvitationType.ADMIN " +
		"AND i.status = com.ria.olita.tech.silingan.entity.InvitationStatus.PENDING AND i.expiresAt > :now) " +
		"ORDER BY CASE WHEN NOT EXISTS (SELECT 1 FROM UserCommunity uc JOIN uc.user u WHERE uc.community = c " +
		"AND uc.role = com.ria.olita.tech.silingan.entity.SilinganRealmRole.COMMUNITY_ADMIN " +
		"AND u.status = com.ria.olita.tech.silingan.entity.UserStatus.ACTIVE) THEN 0 " +
		"WHEN c.status <> com.ria.olita.tech.silingan.entity.CommunityStatus.ACTIVE THEN 1 ELSE 2 END, " +
		"c.name ASC, c.id ASC")
	List<AttentionCommunityRow> findNeedingAttention(@Param("now") LocalDateTime now, Pageable pageable);
}
