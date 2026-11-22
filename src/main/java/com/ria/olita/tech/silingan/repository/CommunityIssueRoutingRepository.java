package com.ria.olita.tech.silingan.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ria.olita.tech.silingan.entity.CommunityIssueRouting;

@Repository
public interface CommunityIssueRoutingRepository extends JpaRepository<CommunityIssueRouting, UUID> {

	@Query("""
		SELECT r
		FROM CommunityIssueRouting r
		LEFT JOIN FETCH r.assignedStaffMembership m
		LEFT JOIN FETCH m.user
		WHERE r.communityId = :communityId
		""")
	List<CommunityIssueRouting> findByCommunityIdWithStaff(@Param("communityId") UUID communityId);

	@Query("""
		SELECT r
		FROM CommunityIssueRouting r
		LEFT JOIN FETCH r.assignedStaffMembership m
		LEFT JOIN FETCH m.user
		WHERE r.communityId = :communityId
			AND r.categoryId = :categoryId
		""")
	Optional<CommunityIssueRouting> findByCommunityIdAndCategoryId(
		@Param("communityId") UUID communityId,
		@Param("categoryId") UUID categoryId
	);

	@Query("""
		SELECT r
		FROM CommunityIssueRouting r
		JOIN FETCH r.category
		WHERE r.communityId = :communityId
			AND r.assignedStaffMembershipId = :membershipId
			AND r.active = true
		""")
	List<CommunityIssueRouting> findActiveByCommunityIdAndStaffMembershipId(
		@Param("communityId") UUID communityId,
		@Param("membershipId") UUID membershipId
	);

	/** Active staff routes held by a user in any community; user deactivation is global, so all are affected. */
	@Query("""
		SELECT r
		FROM CommunityIssueRouting r
		JOIN FETCH r.category c
		JOIN r.assignedStaffMembership m
		WHERE m.userId = :userId
			AND r.active = true
		ORDER BY c.name
		""")
	List<CommunityIssueRouting> findActiveByStaffUserId(@Param("userId") UUID userId);
}
