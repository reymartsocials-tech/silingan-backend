package com.ria.olita.tech.silingan.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ria.olita.tech.silingan.entity.Issue;
import com.ria.olita.tech.silingan.entity.IssueStatus;

@Repository
public interface IssueRepository extends JpaRepository<Issue, UUID> {

	Page<Issue> findByCommunityId(UUID communityId, Pageable pageable);

	Page<Issue> findByCommunityIdAndStatus(UUID communityId, IssueStatus status, Pageable pageable);

	Page<Issue> findByCommunityIdAndCategoryId(UUID communityId, UUID categoryId, Pageable pageable);

	Page<Issue> findByReporterIdAndCommunityId(UUID reporterId, UUID communityId, Pageable pageable);
}