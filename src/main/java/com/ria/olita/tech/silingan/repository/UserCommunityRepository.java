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
				:searchTerm IS NULL
				OR LOWER(COALESCE(u.firstName, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
				OR LOWER(COALESCE(u.lastName, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
				OR LOWER(CONCAT(COALESCE(u.firstName, ''), CONCAT(' ', COALESCE(u.lastName, '')))) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
				OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
				OR LOWER(COALESCE(u.mobileNumber, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
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
		@org.springframework.data.repository.query.Param("userId") UUID userId,
		@org.springframework.data.repository.query.Param("communityId") UUID communityId,
		@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now
	);

	@Query("SELECT COUNT(uc) FROM UserCommunity uc " +
		"JOIN uc.user u " +
		"WHERE uc.community.id = :communityId AND uc.role = :role AND u.status = :userStatus")
	int countByCommunityIdAndRoleAndUserStatus(
		@org.springframework.data.repository.query.Param("communityId") UUID communityId,
		@org.springframework.data.repository.query.Param("role") SilinganRealmRole role,
		@org.springframework.data.repository.query.Param("userStatus") com.ria.olita.tech.silingan.entity.UserStatus userStatus
	);
}
