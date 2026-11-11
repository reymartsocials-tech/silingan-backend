package com.ria.olita.tech.silingan.repository;

import com.ria.olita.tech.silingan.entity.Announcement;
import com.ria.olita.tech.silingan.entity.AnnouncementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID>, JpaSpecificationExecutor<Announcement> {

    @EntityGraph(attributePaths = {"community"})
    @Query("SELECT a FROM Announcement a WHERE a.community.id = :communityId ORDER BY a.pinned DESC, a.createdAt DESC")
    Page<Announcement> findByCommunityId(@Param("communityId") UUID communityId, Pageable pageable);

    @EntityGraph(attributePaths = {"community"})
    @Query("SELECT a FROM Announcement a WHERE a.community.id = :communityId AND a.status = :status " +
            "AND (a.publishAt IS NULL OR a.publishAt <= CURRENT_TIMESTAMP) " +
            "ORDER BY a.pinned DESC, a.createdAt DESC")
    Page<Announcement> findActiveByCommunityId(@Param("communityId") UUID communityId, 
                                               @Param("status") AnnouncementStatus status,
                                               Pageable pageable);

    @EntityGraph(attributePaths = {"community"})
    @Query("SELECT a FROM Announcement a WHERE a.id = ?1")
    Optional<Announcement> findByIdWithDetails(UUID id);

    /**
     * Resident-facing listing. Visibility (community ownership, PUBLISHED status, a real
     * publication timestamp and any pending schedule) is enforced in the query itself so drafts
     * and other communities' announcements are never loaded. Soft-deleted rows are excluded by
     * the entity's {@code @SQLRestriction}.
     */
    @Query("SELECT a FROM Announcement a WHERE a.community.id = :communityId " +
            "AND a.status = com.ria.olita.tech.silingan.entity.AnnouncementStatus.PUBLISHED " +
            "AND a.publishedAt IS NOT NULL " +
            "AND (a.publishAt IS NULL OR a.publishAt <= CURRENT_TIMESTAMP) " +
            "ORDER BY a.publishedAt DESC, a.id DESC")
    Page<Announcement> findPublishedByCommunityId(@Param("communityId") UUID communityId, Pageable pageable);

    /**
     * Resident-facing detail lookup. The announcement is resolved by id <em>and</em> community in a
     * single query so an inaccessible announcement is indistinguishable from a missing one.
     */
    @Query("SELECT a FROM Announcement a WHERE a.id = :id AND a.community.id = :communityId " +
            "AND a.status = com.ria.olita.tech.silingan.entity.AnnouncementStatus.PUBLISHED " +
            "AND a.publishedAt IS NOT NULL " +
            "AND (a.publishAt IS NULL OR a.publishAt <= CURRENT_TIMESTAMP)")
    Optional<Announcement> findPublishedByIdAndCommunityId(@Param("id") UUID id, @Param("communityId") UUID communityId);

    @EntityGraph(attributePaths = {"community"})
    @Override
    Page<Announcement> findAll(Specification<Announcement> spec, Pageable pageable);
}
