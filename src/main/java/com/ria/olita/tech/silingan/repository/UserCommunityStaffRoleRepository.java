package com.ria.olita.tech.silingan.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ria.olita.tech.silingan.entity.UserCommunityStaffRole;

@Repository
public interface UserCommunityStaffRoleRepository extends JpaRepository<UserCommunityStaffRole, UUID> {

	Optional<UserCommunityStaffRole> findByUserIdAndCommunityId(UUID userId, UUID communityId);

	Optional<UserCommunityStaffRole> findByUserIdAndCommunityIdAndActiveTrue(UUID userId, UUID communityId);

	List<UserCommunityStaffRole> findByCommunityIdAndUserIdInAndActiveTrue(UUID communityId, Collection<UUID> userIds);
}
