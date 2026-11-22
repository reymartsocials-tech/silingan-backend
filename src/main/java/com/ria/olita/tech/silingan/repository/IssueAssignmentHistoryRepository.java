package com.ria.olita.tech.silingan.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ria.olita.tech.silingan.entity.IssueAssignmentHistory;

@Repository
public interface IssueAssignmentHistoryRepository extends JpaRepository<IssueAssignmentHistory, UUID> {

	List<IssueAssignmentHistory> findByIssueIdOrderByChangedAtAscIdAsc(UUID issueId);
}
