package com.ria.olita.tech.silingan.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.UserCommunity;

@Repository
public interface UserCommunityRepository extends JpaRepository<UserCommunity, UUID> {

	@Query("SELECT CASE WHEN COUNT(uc) > 0 THEN true ELSE false END " +
			"FROM UserCommunity uc " +
			"JOIN uc.user u " +
			"WHERE uc.community.id = :communityId AND uc.role = :role AND u.status = 'ACTIVE'")
	boolean hasRoleInCommunity(UUID communityId, SilinganRealmRole role);

	Optional<UserCommunity> findByUserIdAndCommunityId(UUID userId, UUID communityId);

	@Query("SELECT uc FROM UserCommunity uc " +
			"JOIN uc.user u " +
			"WHERE uc.user.id = :userId " +
			"AND uc.community.id = :communityId " +
			"AND u.status = 'ACTIVE'")
	Optional<UserCommunity> findByUserIdAndCommunityIdAndUserStatusActive(UUID userId, UUID communityId);

	@Query("SELECT CASE WHEN COUNT(uc) > 0 THEN true ELSE false END " +
			"FROM UserCommunity uc " +
			"JOIN uc.user u " +
			"WHERE uc.community.id = :communityId " +
			"AND LOWER(u.email) = LOWER(:email) " +
			"AND u.status = 'ACTIVE'")
	boolean existsActiveMemberByEmailAndCommunityId(UUID communityId, String email);

	@Query("""
		SELECT uc
		FROM UserCommunity uc
		JOIN FETCH uc.community c
		WHERE uc.user.id = :userId
			AND uc.user.status = 'ACTIVE'
		ORDER BY c.name
		""")
	List<UserCommunity> findActiveByUserIdWithCommunity(UUID userId);

	@Query("""
		SELECT uc
		FROM UserCommunity uc
		JOIN FETCH uc.user u
		WHERE uc.community.id = :communityId
			AND uc.user.status = 'ACTIVE'
			AND uc.role IN :roles
			AND (
				CAST(:searchTerm AS string) IS NULL
				OR LOWER(COALESCE(u.firstName, '')) LIKE LOWER(CONCAT('%', CAST(:searchTerm AS string), '%'))
				OR LOWER(COALESCE(u.lastName, '')) LIKE LOWER(CONCAT('%', CAST(:searchTerm AS string), '%'))
				OR LOWER(CONCAT(COALESCE(u.firstName, ''), CONCAT(' ', COALESCE(u.lastName, '')))) LIKE LOWER(CONCAT('%', CAST(:searchTerm AS string), '%'))
				OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', CAST(:searchTerm AS string), '%'))
				OR LOWER(COALESCE(u.mobileNumber, '')) LIKE LOWER(CONCAT('%', CAST(:searchTerm AS string), '%'))
			)
		ORDER BY u.firstName, u.lastName
		""")
	List<UserCommunity> findStaffByCommunityWithFilters(UUID communityId, Set<SilinganRealmRole> roles, String searchTerm);

	@Query("""
		SELECT uc
		FROM UserCommunity uc
		JOIN FETCH uc.user u
		LEFT JOIN FETCH u.createdBy
		LEFT JOIN FETCH u.updatedBy
		WHERE uc.user.id = :userId
			AND uc.community.id = :communityId
		""")
	@EntityGraph(attributePaths = {"user"})
	Optional<UserCommunity> findByUserIdAndCommunityIdWithDetails(UUID userId, UUID communityId);

	@Query("""
		SELECT uc
		FROM UserCommunity uc
		JOIN FETCH uc.user u
		WHERE uc.id = :membershipId
		""")
	Optional<UserCommunity> findByIdWithUser(@Param("membershipId") UUID membershipId);

	/** Active members who triage the PMO Review Queue: community admins and PMO staff. */
	@Query("""
		SELECT uc.userId
		FROM UserCommunity uc
		JOIN uc.user u
		WHERE uc.communityId = :communityId
			AND u.status = 'ACTIVE'
			AND (
				uc.role = com.ria.olita.tech.silingan.entity.SilinganRealmRole.COMMUNITY_ADMIN
				OR uc.roleCode IN (
					com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode.COMMUNITY_ADMIN,
					com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode.PMO_STAFF
				)
			)
		ORDER BY uc.userId
		""")
	List<UUID> findActiveTriageUserIdsByCommunityId(@Param("communityId") UUID communityId);

	// Staff role query methods
	@Query("SELECT uc FROM UserCommunity uc " +
			"WHERE uc.user.id = :userId " +
			"AND uc.community.id = :communityId " +
			"AND uc.roleCode IS NOT NULL")
	Optional<UserCommunity> findByUserIdAndCommunityIdWithStaffRole(UUID userId, UUID communityId);

	@Query("SELECT uc FROM UserCommunity uc " +
			"WHERE uc.user.id = :userId " +
			"AND uc.community.id IN :communityIds " +
			"AND uc.roleCode IS NOT NULL")
	List<UserCommunity> findByUserIdAndCommunityIdInWithStaffRole(UUID userId, Collection<UUID> communityIds);

	@Query("SELECT uc FROM UserCommunity uc " +
			"WHERE uc.community.id = :communityId " +
			"AND uc.user.id IN :userIds " +
			"AND uc.roleCode IS NOT NULL")
	List<UserCommunity> findByCommunityIdAndUserIdInWithStaffRole(UUID communityId, Collection<UUID> userIds);

	@Query("""
		SELECT uc.userId
		FROM UserCommunity uc
		JOIN uc.user u
		WHERE uc.communityId = :communityId
			AND uc.role = :role
			AND u.status = 'ACTIVE'
		ORDER BY uc.userId
		""")
	List<UUID> findActiveUserIdsByCommunityIdAndRole(UUID communityId, SilinganRealmRole role, Pageable pageable);

	/**
	 * Deactivate user and membership when invitation is revoked.
	 * Marks the user as INACTIVE and unsets the staff role assignment.
	 */
	@Modifying
	@Query("UPDATE UserCommunity uc SET " +
		"uc.roleCode = null, " +
		"uc.assignedAt = null, " +
		"uc.assignedBy = null, " +
		"uc.updatedAt = :now " +
		"WHERE uc.user.id = :userId " +
		"AND uc.community.id = :communityId")
	int deactivateStaffRoleAssignment(
		@Param("userId") UUID userId,
		@Param("communityId") UUID communityId,
		@Param("now") java.time.LocalDateTime now
	);

	@Query("SELECT COUNT(uc) FROM UserCommunity uc " +
		"JOIN uc.user u " +
		"WHERE uc.community.id = :communityId AND uc.role = :role AND u.status = :userStatus")
	int countByCommunityIdAndRoleAndUserStatus(
		@Param("communityId") UUID communityId,
		@Param("role") SilinganRealmRole role,
		@Param("userStatus") com.ria.olita.tech.silingan.entity.UserStatus userStatus
	);

	@Query("SELECT COUNT(uc) FROM UserCommunity uc " +
		"JOIN uc.user u " +
		"WHERE uc.community.id = :communityId AND uc.role IN :roles AND u.status = :userStatus")
	long countByCommunityIdAndRoleInAndUserStatus(
		@Param("communityId") UUID communityId,
		@Param("roles") Collection<SilinganRealmRole> roles,
		@Param("userStatus") com.ria.olita.tech.silingan.entity.UserStatus userStatus
	);

	/** Distinct people holding an active membership with the role in any existing community. */
	@Query("SELECT COUNT(DISTINCT u.id) FROM UserCommunity uc " +
		"JOIN uc.user u JOIN uc.community c " +
		"WHERE uc.role = :role AND u.status = :userStatus")
	long countDistinctUsersByRoleAndUserStatus(
		@Param("role") SilinganRealmRole role,
		@Param("userStatus") com.ria.olita.tech.silingan.entity.UserStatus userStatus
	);
}
