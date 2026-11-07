package com.ria.olita.tech.silingan.repository;

import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.CommunityType;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
