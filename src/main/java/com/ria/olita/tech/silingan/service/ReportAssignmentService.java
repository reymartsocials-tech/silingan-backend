package com.ria.olita.tech.silingan.service;

import java.util.UUID;

import com.ria.olita.tech.silingan.entity.Issue;
import com.ria.olita.tech.silingan.entity.IssueRoutingDestination;
import com.ria.olita.tech.silingan.entity.User;

public interface ReportAssignmentService {

	/** Resolves the community/category route for a newly created report, falling back to the PMO Review Queue. */
	void assignOnSubmission(Issue issue);

	/** Manually moves a report to another eligible staff member or to the PMO Review Queue. */
	void reassign(Issue issue, IssueRoutingDestination destination, UUID staffMembershipId, String reason, User actor);
}
