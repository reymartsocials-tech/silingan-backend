package com.ria.olita.tech.silingan.repository;

import com.ria.olita.tech.silingan.entity.CommunityAdminInvitation;
import com.ria.olita.tech.silingan.entity.CommunityAdminInvitationStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommunityAdminInvitationRepository extends JpaRepository<CommunityAdminInvitation, UUID> {

	boolean existsByCommunityIdAndStatus(UUID communityId, CommunityAdminInvitationStatus status);

	List<CommunityAdminInvitation> findByKeycloakUserIdAndStatusAndCommunityId(String keycloakUserId, CommunityAdminInvitationStatus status, UUID communityId);


	@Query("""
		SELECT i
		FROM CommunityAdminInvitation i
		WHERE i.communityId = :communityId  AND  i.status = :status
		ORDER BY i.invitedAt DESC
	""")
	Page<CommunityAdminInvitation> findByFilters(
		@Param("communityId") UUID communityId,
		@Param("status") CommunityAdminInvitationStatus status,
		Pageable pageable
	);

	@Query("""
		SELECT i
		FROM CommunityAdminInvitation i where i.status = :status
		ORDER BY i.invitedAt DESC
	""")
	Page<CommunityAdminInvitation> findByFilters(@Param("status") CommunityAdminInvitationStatus status, Pageable pageable);
}




