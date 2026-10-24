package com.ria.olita.tech.silingan.repository.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.ria.olita.tech.silingan.dto.req.ReportFilterCriteria;
import com.ria.olita.tech.silingan.entity.Issue;

import jakarta.persistence.criteria.Predicate;

public class IssueSpecifications {

	public static Specification<Issue> filterByCommunityAndCriteria(UUID communityId, ReportFilterCriteria criteria) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();

			// Filter by community if provided
			if (communityId != null) {
				predicates.add(cb.equal(root.get("communityId"), communityId));
			}
			
			// Always filter non-deleted
			predicates.add(cb.equal(root.get("deleted"), false));

			if (criteria == null) {
				return cb.and(predicates.toArray(new Predicate[0]));
			}

			// Status filter
			if (criteria.getStatus() != null) {
				predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
			}

			// Urgency level filter
			if (criteria.getUrgencyLevel() != null) {
				predicates.add(cb.equal(root.get("urgencyLevel"), criteria.getUrgencyLevel()));
			}

			// Category filter
			if (criteria.getCategoryId() != null) {
				predicates.add(cb.equal(root.get("category").get("id"), criteria.getCategoryId()));
			}

			// Assigned to filter
			if (criteria.getAssignedToId() != null) {
				predicates.add(cb.equal(root.get("assignedTo").get("id"), criteria.getAssignedToId()));
			}

			// Reporter filter
			if (criteria.getReporterId() != null) {
				predicates.add(cb.equal(root.get("reporter").get("id"), criteria.getReporterId()));
			}

			// Assigned status filter (true = has assignedTo, false = no assignedTo)
			if (criteria.getAssigned() != null) {
				if (criteria.getAssigned()) {
					predicates.add(cb.isNotNull(root.get("assignedTo")));
				} else {
					predicates.add(cb.isNull(root.get("assignedTo")));
				}
			}

			// Keyword search (title and description)
			if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
				String keyword = "%" + criteria.getKeyword().toLowerCase() + "%";
				Predicate titlePredicate = cb.like(cb.lower(root.get("title")), keyword);
				Predicate descPredicate = cb.like(cb.lower(root.get("description")), keyword);
				predicates.add(cb.or(titlePredicate, descPredicate));
			}

			// Location filter
			if (criteria.getLocation() != null && !criteria.getLocation().isBlank()) {
				String location = "%" + criteria.getLocation().toLowerCase() + "%";
				predicates.add(cb.like(cb.lower(root.get("location")), location));
			}

			// Created date range filter
			if (criteria.getCreatedAtFrom() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.getCreatedAtFrom()));
			}
			if (criteria.getCreatedAtTo() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), criteria.getCreatedAtTo()));
			}

			// Resolved date range filter
			if (criteria.getResolvedAtFrom() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("resolvedAt"), criteria.getResolvedAtFrom()));
			}
			if (criteria.getResolvedAtTo() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("resolvedAt"), criteria.getResolvedAtTo()));
			}

			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	public static Specification<Issue> filterByReporterAndCriteria(UUID communityId, UUID reporterId, ReportFilterCriteria criteria) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();

			// Always filter by community and reporter
			predicates.add(cb.equal(root.get("communityId"), communityId));
			predicates.add(cb.equal(root.get("reporter").get("id"), reporterId));

			if (criteria == null) {
				return cb.and(predicates.toArray(new Predicate[0]));
			}

			// Status filter
			if (criteria.getStatus() != null) {
				predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
			}

			// Urgency level filter
			if (criteria.getUrgencyLevel() != null) {
				predicates.add(cb.equal(root.get("urgencyLevel"), criteria.getUrgencyLevel()));
			}

			// Category filter
			if (criteria.getCategoryId() != null) {
				predicates.add(cb.equal(root.get("category").get("id"), criteria.getCategoryId()));
			}

			// Assigned to filter
			if (criteria.getAssignedToId() != null) {
				predicates.add(cb.equal(root.get("assignedTo").get("id"), criteria.getAssignedToId()));
			}

			// Assigned status filter (true = has assignedTo, false = no assignedTo)
			if (criteria.getAssigned() != null) {
				if (criteria.getAssigned()) {
					predicates.add(cb.isNotNull(root.get("assignedTo")));
				} else {
					predicates.add(cb.isNull(root.get("assignedTo")));
				}
			}

			// Keyword search (title and description)
			if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
				String keyword = "%" + criteria.getKeyword().toLowerCase() + "%";
				Predicate titlePredicate = cb.like(cb.lower(root.get("title")), keyword);
				Predicate descPredicate = cb.like(cb.lower(root.get("description")), keyword);
				predicates.add(cb.or(titlePredicate, descPredicate));
			}

			// Location filter
			if (criteria.getLocation() != null && !criteria.getLocation().isBlank()) {
				String location = "%" + criteria.getLocation().toLowerCase() + "%";
				predicates.add(cb.like(cb.lower(root.get("location")), location));
			}

			// Created date range filter
			if (criteria.getCreatedAtFrom() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), criteria.getCreatedAtFrom()));
			}
			if (criteria.getCreatedAtTo() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), criteria.getCreatedAtTo()));
			}

			// Resolved date range filter
			if (criteria.getResolvedAtFrom() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("resolvedAt"), criteria.getResolvedAtFrom()));
			}
			if (criteria.getResolvedAtTo() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("resolvedAt"), criteria.getResolvedAtTo()));
			}

			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}
}
