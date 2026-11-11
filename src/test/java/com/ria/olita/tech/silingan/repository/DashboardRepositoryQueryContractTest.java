package com.ria.olita.tech.silingan.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class DashboardRepositoryQueryContractTest {

	private static String queryOf(Class<?> repository, String methodName) {
		Method method = Arrays.stream(repository.getMethods())
			.filter(m -> m.getName().equals(methodName))
			.findFirst()
			.orElseThrow();
		return method.getAnnotation(Query.class).value();
	}

	@Test
	void statusGroupingIsCommunityScopedAndGroupedInTheDatabase() {
		String jpql = queryOf(IssueRepository.class, "countByStatusForCommunity");

		assertThat(jpql).contains("i.communityId = :communityId");
		assertThat(jpql).contains("COUNT(i)").contains("GROUP BY i.status");
	}

	@Test
	void recentReportsAreScopedNewestFirstAndOmitSensitiveColumns() {
		String jpql = queryOf(IssueRepository.class, "findRecentForDashboard");

		assertThat(jpql).contains("i.communityId = :communityId");
		assertThat(jpql).contains("ORDER BY i.createdAt DESC, i.id DESC");
		assertThat(jpql).doesNotContain("i.description").doesNotContain("i.reporter").doesNotContain("i.location");
	}

	@Test
	void publishedCountUsesPublicationTimeAndHalfOpenRange() {
		String jpql = queryOf(AnnouncementRepository.class, "countPublishedBetween");

		assertThat(jpql).contains("a.community.id = :communityId");
		assertThat(jpql).contains("AnnouncementStatus.PUBLISHED");
		assertThat(jpql).contains("a.publishedAt >= :from AND a.publishedAt < :to");
		assertThat(jpql).doesNotContain("createdAt");
	}

	@Test
	void recentAnnouncementsArePublishedScopedAndNewestPublishedFirst() {
		String jpql = queryOf(AnnouncementRepository.class, "findRecentPublishedForDashboard");

		assertThat(jpql).contains("a.community.id = :communityId");
		assertThat(jpql).contains("AnnouncementStatus.PUBLISHED");
		assertThat(jpql).contains("ORDER BY a.publishedAt DESC, a.id DESC");
		assertThat(jpql).contains("SUBSTRING(a.content, 1, 150)");
	}

	@Test
	void activeMembersCountIsCommunityScopedAndRequiresActiveUser() {
		String jpql = queryOf(UserCommunityRepository.class, "countByCommunityIdAndRoleInAndUserStatus");

		assertThat(jpql).contains("uc.community.id = :communityId");
		assertThat(jpql).contains("uc.role IN :roles").contains("u.status = :userStatus");
	}
}
