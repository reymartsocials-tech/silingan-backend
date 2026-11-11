package com.ria.olita.tech.silingan.repository;

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
}