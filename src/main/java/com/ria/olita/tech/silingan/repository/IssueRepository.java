package com.ria.olita.tech.silingan.repository;

import org.springframework.data.repository.query.Param;
import com.ria.olita.tech.silingan.dto.res.DashboardReportStatusResponse;
import com.ria.olita.tech.silingan.dto.res.DashboardReportResponse;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ria.olita.tech.silingan.entity.Issue;
import com.ria.olita.tech.silingan.entity.IssueStatus;

@Repository
public interface IssueRepository extends JpaRepository<Issue, UUID>, JpaSpecificationExecutor<Issue> {

	@EntityGraph(attributePaths = {"reporter", "category", "community"})
	Page<Issue> findByCommunityId(UUID communityId, Pageable pageable);

	@EntityGraph(attributePaths = {"reporter", "category", "community"})
	Page<Issue> findByCommunityIdAndStatus(UUID communityId, IssueStatus status, Pageable pageable);

	@EntityGraph(attributePaths = {"reporter", "category", "community"})
	Page<Issue> findByCommunityIdAndCategoryId(UUID communityId, UUID categoryId, Pageable pageable);

	@EntityGraph(attributePaths = {"reporter", "category", "community"})
	Page<Issue> findByReporterIdAndCommunityId(UUID reporterId, UUID communityId, Pageable pageable);

	@EntityGraph(attributePaths = {"reporter", "category", "community"})
	@Query("SELECT i FROM Issue i WHERE i.id = ?1")
	Optional<Issue> findByIdWithDetails(UUID id);

	@EntityGraph(attributePaths = {"reporter", "category", "community", "assignedTo"})
	@Override
	Page<Issue> findAll(Specification<Issue> spec, Pageable pageable);

	/** Grouped in the database; soft-deleted rows are excluded by the entity's {@code @SQLRestriction}. */
	@Query("SELECT new com.ria.olita.tech.silingan.dto.res.DashboardReportStatusResponse(i.status, COUNT(i)) " +
			"FROM Issue i WHERE i.communityId = :communityId GROUP BY i.status")
	List<DashboardReportStatusResponse> countByStatusForCommunity(@Param("communityId") UUID communityId);

	/** Preview projection; the caller bounds the result through {@code pageable} (no count query is issued). */
	@Query("SELECT new com.ria.olita.tech.silingan.dto.res.DashboardReportResponse(" +
			"i.id, i.title, COALESCE(i.customCategory, c.name), i.status, i.urgencyLevel, i.createdAt) " +
			"FROM Issue i JOIN i.category c WHERE i.communityId = :communityId " +
			"ORDER BY i.createdAt DESC, i.id DESC")
	List<DashboardReportResponse> findRecentForDashboard(@Param("communityId") UUID communityId, Pageable pageable);
}
