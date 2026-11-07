package com.ria.olita.tech.silingan.repository.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.ria.olita.tech.silingan.dto.req.DirectoryContactFilterCriteria;
import com.ria.olita.tech.silingan.entity.DirectoryContact;
import com.ria.olita.tech.silingan.entity.DirectoryContactCategory;
import com.ria.olita.tech.silingan.entity.DirectoryContactType;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Verifies that community, active and deletion filtering happen inside the query and that the
 * emergency-first ordering is attached, using a recording Criteria API rather than a database.
 */
class DirectoryContactSpecificationsTest {

	@SuppressWarnings("unchecked")
	private final Root<DirectoryContact> root = Mockito.mock(Root.class);
	private final CriteriaBuilder cb = Mockito.mock(CriteriaBuilder.class);

	/** Attribute names the specification touched. */
	private final List<String> requestedPaths = new ArrayList<>();

	private CriteriaQuery<?> criteriaQuery(Class<?> resultType) {
		CriteriaQuery<?> query = Mockito.mock(CriteriaQuery.class);
		Mockito.<Class<?>>when(query.getResultType()).thenReturn(resultType);
		return query;
	}

	@SuppressWarnings("unchecked")
	private void wireCriteriaApi() {
		when(root.get(anyString())).thenAnswer(invocation -> {
			requestedPaths.add(invocation.getArgument(0));
			return Mockito.mock(Path.class);
		});

		when(cb.equal(any(Expression.class), any(Object.class))).thenReturn(Mockito.mock(Predicate.class));
		when(cb.isTrue(any(Expression.class))).thenReturn(Mockito.mock(Predicate.class));
		when(cb.like(any(Expression.class), anyString())).thenReturn(Mockito.mock(Predicate.class));
		when(cb.lower(any(Expression.class))).thenReturn(Mockito.mock(Expression.class));
		when(cb.disjunction()).thenReturn(Mockito.mock(Predicate.class));
		when(cb.and(any(Predicate[].class))).thenReturn(Mockito.mock(Predicate.class));
		when(cb.asc(any(Expression.class))).thenReturn(Mockito.mock(Order.class));

		CriteriaBuilder.SimpleCase<DirectoryContactCategory, Integer> simpleCase =
			Mockito.mock(CriteriaBuilder.SimpleCase.class);
		when(cb.selectCase(any(Expression.class))).thenReturn(simpleCase);
		when(simpleCase.when(any(DirectoryContactCategory.class), anyInt())).thenReturn(simpleCase);
		when(simpleCase.otherwise(anyInt())).thenReturn(Mockito.mock(Expression.class));
	}

	@Test
	void residentSpecificationShouldFilterByCommunityActiveAndNotDeleted() {
		wireCriteriaApi();
		UUID communityId = UUID.randomUUID();

		DirectoryContactSpecifications.forResident(communityId)
			.toPredicate(root, criteriaQuery(DirectoryContact.class), cb);

		assertThat(requestedPaths).contains("communityId", "active");
		verify(cb).equal(any(Expression.class), eq(communityId));
		verify(cb).equal(any(Expression.class), eq(false));
		verify(cb).isTrue(any(Expression.class));
		verify(cb, never()).disjunction();
	}

	@Test
	void residentSpecificationShouldOrderEmergencyFirstThenDisplayOrderNameAndId() {
		wireCriteriaApi();
		CriteriaQuery<?> query = criteriaQuery(DirectoryContact.class);

		DirectoryContactSpecifications.forResident(UUID.randomUUID()).toPredicate(root, query, cb);

		ArgumentCaptor<Order[]> captor = ArgumentCaptor.forClass(Order[].class);
		verify(query).orderBy(captor.capture());
		assertThat(captor.getValue()).hasSize(4);

		// Every category is mapped to its declared priority, so emergency sorts first.
		verify(cb).selectCase(any(Expression.class));
		assertThat(requestedPaths).contains("category", "displayOrder", "displayName", "id");
	}

	@Test
	void countQueriesShouldNotCarryAnOrderByClause() {
		wireCriteriaApi();
		CriteriaQuery<?> countQuery = criteriaQuery(Long.class);

		DirectoryContactSpecifications.forStaff(UUID.randomUUID(), null).toPredicate(root, countQuery, cb);

		verify(countQuery, never()).orderBy(any(Order[].class));
	}

	@Test
	void staffSpecificationShouldApplyEverySuppliedFilterWithinTheCommunity() {
		wireCriteriaApi();
		UUID communityId = UUID.randomUUID();
		DirectoryContactFilterCriteria criteria = DirectoryContactFilterCriteria.builder()
			.category(DirectoryContactCategory.EMERGENCY_SERVICE)
			.contactType(DirectoryContactType.POLICE)
			.active(true)
			.keyword("  Police  ")
			.build();

		DirectoryContactSpecifications.forStaff(communityId, criteria)
			.toPredicate(root, criteriaQuery(DirectoryContact.class), cb);

		assertThat(requestedPaths).contains("communityId", "category", "contactType", "active", "displayName");
		verify(cb).equal(any(Expression.class), eq(communityId));
		verify(cb).equal(any(Expression.class), eq(DirectoryContactCategory.EMERGENCY_SERVICE));
		verify(cb).equal(any(Expression.class), eq(DirectoryContactType.POLICE));
		verify(cb).equal(any(Expression.class), eq(true));
		// The keyword is trimmed, lower-cased and wrapped for a contains match.
		verify(cb).like(any(Expression.class), eq("%police%"));
	}

	@Test
	void staffSpecificationShouldNotFilterOnOmittedCriteria() {
		wireCriteriaApi();

		DirectoryContactSpecifications.forStaff(UUID.randomUUID(), DirectoryContactFilterCriteria.builder().build())
			.toPredicate(root, criteriaQuery(DirectoryContact.class), cb);

		// Only the community and soft-delete predicates are applied.
		assertThat(requestedPaths).doesNotContain("contactType");
		verify(cb, never()).like(any(Expression.class), anyString());
	}

	@Test
	void blankKeywordShouldNotProduceAFilter() {
		wireCriteriaApi();

		DirectoryContactSpecifications.forStaff(
				UUID.randomUUID(),
				DirectoryContactFilterCriteria.builder().keyword("   ").build())
			.toPredicate(root, criteriaQuery(DirectoryContact.class), cb);

		verify(cb, never()).like(any(Expression.class), anyString());
	}

	@Test
	void missingCommunityShouldMatchNothingInsteadOfEveryCommunity() {
		wireCriteriaApi();

		DirectoryContactSpecifications.forResident(null)
			.toPredicate(root, criteriaQuery(DirectoryContact.class), cb);

		// A null community must never widen the query to the whole table.
		verify(cb).disjunction();
		verify(cb, never()).equal(any(Expression.class), any(UUID.class));
	}
}
