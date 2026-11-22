package com.ria.olita.tech.silingan.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ria.olita.tech.silingan.entity.IssueResolutionDecision;

@Repository
public interface IssueResolutionDecisionRepository extends JpaRepository<IssueResolutionDecision, UUID> {
}
