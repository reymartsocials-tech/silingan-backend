package com.ria.olita.tech.silingan.repository.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.ria.olita.tech.silingan.dto.req.AnnouncementFilterCriteria;
import com.ria.olita.tech.silingan.entity.Announcement;

import jakarta.persistence.criteria.Predicate;

public class AnnouncementSpecifications {

	public static Specification<Announcement> filterByCommunityAndCriteria(UUID communityId, AnnouncementFilterCriteria criteria) {
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

			if (criteria.getStatus() != null) {
				predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
			}

			if (criteria.getCategory() != null) {
				predicates.add(cb.equal(root.get("category"), criteria.getCategory()));
			}

			if (criteria.getPinned() != null) {
				predicates.add(cb.equal(root.get("pinned"), criteria.getPinned()));
			}

			if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
				String keyword = "%" + criteria.getKeyword().toLowerCase() + "%";
				Predicate titlePredicate = cb.like(cb.lower(root.get("title")), keyword);
				Predicate contentPredicate = cb.like(cb.lower(root.get("content")), keyword);
				predicates.add(cb.or(titlePredicate, contentPredicate));
			}

			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}
}
