package com.ria.olita.tech.silingan.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.UserCommunity;

@Repository
public interface UserCommunityRepository extends JpaRepository<UserCommunity, UUID> {

	@Query("SELECT CASE WHEN COUNT(uc) > 0 THEN true ELSE false END " +
			"FROM UserCommunity uc " +
			"WHERE uc.community.id = :communityId AND uc.role = :role")
	boolean hasRoleInCommunity(UUID communityId, SilinganRealmRole role);

	Optional<UserCommunity> findByUserIdAndCommunityId(UUID userId, UUID communityId);

	@Query("""
		SELECT uc
		FROM UserCommunity uc
		JOIN FETCH uc.user u
		WHERE uc.community.id = :communityId
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
}
