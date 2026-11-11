package com.ria.olita.tech.silingan.repository.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.ria.olita.tech.silingan.dto.req.DirectoryContactFilterCriteria;
import com.ria.olita.tech.silingan.entity.DirectoryContact;
import com.ria.olita.tech.silingan.entity.DirectoryContactCategory;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

public class DirectoryContactSpecifications {

	private DirectoryContactSpecifications() {
	}

	/**
	 * Staff view of one community's directory. Both active and inactive contacts are returned
	 * unless the caller filters on status; soft-deleted rows are already excluded by the entity's
	 * {@code @SQLRestriction}.
	 */
	public static Specification<DirectoryContact> forStaff(UUID communityId, DirectoryContactFilterCriteria criteria) {
		return (root, query, cb) -> {
			List<Predicate> predicates = communityScope(root, cb, communityId);

			if (criteria != null) {
				if (criteria.getCategory() != null) {
					predicates.add(cb.equal(root.get("category"), criteria.getCategory()));
				}
				if (criteria.getContactType() != null) {
					predicates.add(cb.equal(root.get("contactType"), criteria.getContactType()));
				}
				if (criteria.getActive() != null) {
					predicates.add(cb.equal(root.get("active"), criteria.getActive()));
				}
				if (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) {
					String keyword = "%" + criteria.getKeyword().trim().toLowerCase() + "%";
					predicates.add(cb.like(cb.lower(root.get("displayName")), keyword));
				}
			}

			applyDirectoryOrder(root, query, cb);
			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	/**
	 * Resident view. The active and community filters are part of the query rather than applied
	 * afterwards, so an inactive or foreign contact is never loaded in the first place.
	 */
	public static Specification<DirectoryContact> forResident(UUID communityId) {
		return (root, query, cb) -> {
			List<Predicate> predicates = communityScope(root, cb, communityId);
			predicates.add(cb.isTrue(root.get("active")));

			applyDirectoryOrder(root, query, cb);
			return cb.and(predicates.toArray(new Predicate[0]));
		};
	}

	private static List<Predicate> communityScope(Root<DirectoryContact> root, CriteriaBuilder cb, UUID communityId) {
		List<Predicate> predicates = new ArrayList<>();
		// A null community would silently widen the query to every community, so it is rejected.
		predicates.add(communityId == null ? cb.disjunction() : cb.equal(root.get("communityId"), communityId));
		predicates.add(cb.equal(root.get("deleted"), false));
		return predicates;
	}

	/**
	 * Emergency contacts first, then the remaining categories in their declared priority. Display
	 * order, display name and finally the identifier break ties so that paging is stable.
	 */
	private static void applyDirectoryOrder(Root<DirectoryContact> root, CriteriaQuery<?> query, CriteriaBuilder cb) {
		// Count queries select a Long and must not carry an ORDER BY clause.
		if (query == null || Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType())) {
			return;
		}

		query.orderBy(
			cb.asc(categoryPriority(root, cb)),
			cb.asc(root.get("displayOrder")),
			cb.asc(cb.lower(root.get("displayName"))),
			cb.asc(root.get("id"))
		);
	}

	/**
	 * The category is stored as a string, so alphabetical ordering would not put emergency first.
	 * Mapping each category to its declared priority keeps the ordering rule in the enum.
	 */
	private static Expression<Integer> categoryPriority(Root<DirectoryContact> root, CriteriaBuilder cb) {
		CriteriaBuilder.SimpleCase<DirectoryContactCategory, Integer> priority =
			cb.selectCase(root.get("category"));

		for (DirectoryContactCategory category : DirectoryContactCategory.values()) {
			priority = priority.when(category, category.getDisplayPriority());
		}

		return priority.otherwise(Integer.MAX_VALUE);
	}
}
