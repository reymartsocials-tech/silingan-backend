package com.ria.olita.tech.silingan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;

import com.ria.olita.tech.silingan.dto.res.ResidentAnnouncementResponse;
import com.ria.olita.tech.silingan.entity.Announcement;
import com.ria.olita.tech.silingan.entity.AnnouncementCategory;
import com.ria.olita.tech.silingan.entity.AnnouncementStatus;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.rbac.CommunityAccess;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.mapper.AnnouncementMapperImpl;
import com.ria.olita.tech.silingan.repository.AnnouncementRepository;
import com.ria.olita.tech.silingan.repository.AnnouncementAcknowledgementRepository;
import com.ria.olita.tech.silingan.repository.AnnouncementHelpfulMarkRepository;
import com.ria.olita.tech.silingan.repository.AttachmentReferenceRepository;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.service.impl.AnnouncementInteractionRecorder;
import com.ria.olita.tech.silingan.service.impl.AnnouncementServiceImpl;

class ResidentAnnouncementServiceImplTest {

	private final AnnouncementRepository announcementRepository = Mockito.mock(AnnouncementRepository.class);
	private final CommunityRepository communityRepository = Mockito.mock(CommunityRepository.class);
	private final UserCommunityRepository userCommunityRepository = Mockito.mock(UserCommunityRepository.class);
	private final UserRepository userRepository = Mockito.mock(UserRepository.class);
	private final AttachmentReferenceRepository attachmentReferenceRepository = Mockito.mock(AttachmentReferenceRepository.class);
	private final AnnouncementAcknowledgementRepository acknowledgementRepository = Mockito.mock(AnnouncementAcknowledgementRepository.class);
	private final AnnouncementHelpfulMarkRepository helpfulMarkRepository = Mockito.mock(AnnouncementHelpfulMarkRepository.class);
	private final AnnouncementInteractionRecorder interactionRecorder = Mockito.mock(AnnouncementInteractionRecorder.class);

	private final AnnouncementServiceImpl service = new AnnouncementServiceImpl(
		announcementRepository,
		communityRepository,
		userCommunityRepository,
		userRepository,
		attachmentReferenceRepository,
		acknowledgementRepository,
		helpfulMarkRepository,
		interactionRecorder,
		new AnnouncementMapperImpl()
	);

	private final UUID communityA = UUID.randomUUID();
	private final UUID communityB = UUID.randomUUID();

	@AfterEach
	void tearDown() {
		UserContextHolder.clear();
	}

	// --- fixtures -------------------------------------------------------------------------

	private UUID givenResidentWithActiveMembership(UUID activeCommunityId) {
		UUID userId = givenAuthenticatedResident(activeCommunityId);
		User user = User.builder().id(userId).username("resident").build();
		when(userCommunityRepository.findByUserIdAndCommunityIdAndUserStatusActive(userId, activeCommunityId))
			.thenReturn(Optional.of(UserCommunity.builder()
				.user(user)
				.userId(userId)
				.communityId(activeCommunityId)
				.role(SilinganRealmRole.RESIDENT)
				.build()));
		return userId;
	}

	private UUID givenAuthenticatedResident(UUID activeCommunityId) {
		UUID userId = UUID.randomUUID();
		UserContextHolder.set(UserContext.builder()
			.userId(userId.toString())
			.communityId(activeCommunityId == null ? null : activeCommunityId.toString())
			.roles(List.of(SilinganRealmRole.RESIDENT))
			.communityAccess(activeCommunityId == null ? null : new CommunityAccess(activeCommunityId, List.of()))
			.build());
		return userId;
	}

	private Announcement publishedAnnouncement(UUID communityId, String title, LocalDateTime publishedAt) {
		Announcement announcement = Announcement.builder()
			.id(UUID.randomUUID())
			.community(Community.builder().id(communityId).build())
			.communityId(communityId)
			.title(title)
			.content(title + " body")
			.category(AnnouncementCategory.ADVISORY)
			.status(AnnouncementStatus.PUBLISHED)
			.build();
		announcement.setPublishedAt(publishedAt);
		announcement.setCreatedAt(publishedAt.minusDays(1));
		return announcement;
	}

	private void givenPage(List<Announcement> announcements) {
		when(announcementRepository.findPublishedByCommunityId(any(), any()))
			.thenReturn(new PageImpl<>(announcements));
		when(attachmentReferenceRepository.findByOwnerTypeAndOwnerIdIn(any(), any())).thenReturn(List.of());
	}

	// --- list -----------------------------------------------------------------------------

	@Test
	void shouldReturnPublishedAnnouncementsOfActiveCommunity() {
		givenResidentWithActiveMembership(communityA);
		LocalDateTime now = LocalDateTime.now();
		givenPage(List.of(
			publishedAnnouncement(communityA, "Newest", now),
			publishedAnnouncement(communityA, "Older", now.minusDays(2))
		));

		Page<ResidentAnnouncementResponse> page =
			service.getPublishedAnnouncementsForResident(PageRequest.of(0, 10));

		assertThat(page.getContent()).hasSize(2);
		assertThat(page.getContent()).extracting(ResidentAnnouncementResponse::title)
			.containsExactly("Newest", "Older");
		assertThat(page.getContent().getFirst().publishedAt()).isEqualTo(now);
	}

	@Test
	void shouldQueryOnlyTheResidentActiveCommunity() {
		givenResidentWithActiveMembership(communityA);
		givenPage(List.of());

		service.getPublishedAnnouncementsForResident(PageRequest.of(0, 10));

		verify(announcementRepository).findPublishedByCommunityId(eq(communityA), any(Pageable.class));
		verify(announcementRepository, never()).findPublishedByCommunityId(eq(communityB), any(Pageable.class));
	}

	@Test
	void shouldNeverUseTheUnfilteredStaffQueriesForResidents() {
		givenResidentWithActiveMembership(communityA);
		givenPage(List.of());

		service.getPublishedAnnouncementsForResident(PageRequest.of(0, 10));

		verify(announcementRepository, never()).findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class));
		verify(announcementRepository, never()).findByCommunityId(any(), any());
	}

	@Test
	void shouldReturnEmptyPageWhenCommunityHasNoPublishedAnnouncements() {
		givenResidentWithActiveMembership(communityA);
		givenPage(List.of());

		Page<ResidentAnnouncementResponse> page =
			service.getPublishedAnnouncementsForResident(PageRequest.of(0, 10));

		assertThat(page.getContent()).isEmpty();
		assertThat(page.getTotalElements()).isZero();
	}

	@Test
	void shouldRejectListingForUnauthenticatedRequest() {
		UserContextHolder.clear();

		assertThatThrownBy(() -> service.getPublishedAnnouncementsForResident(PageRequest.of(0, 10)))
			.isInstanceOf(ForbiddenException.class);
		verify(announcementRepository, never()).findPublishedByCommunityId(any(), any());
	}

	@Test
	void shouldRejectListingWhenNoActiveCommunityIsSelected() {
		givenAuthenticatedResident(null);

		assertThatThrownBy(() -> service.getPublishedAnnouncementsForResident(PageRequest.of(0, 10)))
			.isInstanceOf(ForbiddenException.class)
			.hasMessageContaining("active community");
		verify(announcementRepository, never()).findPublishedByCommunityId(any(), any());
	}

	@Test
	void shouldRejectListingWhenMembershipIsInactive() {
		UUID userId = givenAuthenticatedResident(communityA);
		when(userCommunityRepository.findByUserIdAndCommunityIdAndUserStatusActive(userId, communityA))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getPublishedAnnouncementsForResident(PageRequest.of(0, 10)))
			.isInstanceOf(ForbiddenException.class);
		verify(announcementRepository, never()).findPublishedByCommunityId(any(), any());
	}

	@Test
	void shouldRejectListingWhenMembershipHasNoRole() {
		UUID userId = givenAuthenticatedResident(communityA);
		when(userCommunityRepository.findByUserIdAndCommunityIdAndUserStatusActive(userId, communityA))
			.thenReturn(Optional.of(UserCommunity.builder().userId(userId).communityId(communityA).build()));

		assertThatThrownBy(() -> service.getPublishedAnnouncementsForResident(PageRequest.of(0, 10)))
			.isInstanceOf(ForbiddenException.class);
		verify(announcementRepository, never()).findPublishedByCommunityId(any(), any());
	}

	// --- pagination -----------------------------------------------------------------------

	@Test
	void shouldCapResidentPageSize() {
		givenResidentWithActiveMembership(communityA);
		givenPage(List.of());

		service.getPublishedAnnouncementsForResident(PageRequest.of(0, 5000));

		assertThat(capturedPageable().getPageSize()).isEqualTo(100);
	}

	@Test
	void shouldUseDefaultPageSizeWhenPageableIsMissing() {
		givenResidentWithActiveMembership(communityA);
		givenPage(List.of());

		service.getPublishedAnnouncementsForResident(null);

		assertThat(capturedPageable().getPageSize()).isEqualTo(20);
		assertThat(capturedPageable().getPageNumber()).isZero();
	}

	@Test
	void shouldIgnoreClientSuppliedSortSoPublicationOrderCannotBeOverridden() {
		givenResidentWithActiveMembership(communityA);
		givenPage(List.of());

		service.getPublishedAnnouncementsForResident(
			PageRequest.of(1, 10, Sort.by(Sort.Order.asc("title"))));

		Pageable used = capturedPageable();
		assertThat(used.getSort().isUnsorted()).isTrue();
		assertThat(used.getPageNumber()).isEqualTo(1);
	}

	private Pageable capturedPageable() {
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		verify(announcementRepository).findPublishedByCommunityId(eq(communityA), captor.capture());
		return captor.getValue();
	}

	// --- detail ---------------------------------------------------------------------------

	@Test
	void shouldReturnPublishedAnnouncementDetails() {
		givenResidentWithActiveMembership(communityA);
		LocalDateTime publishedAt = LocalDateTime.now().minusHours(2);
		Announcement announcement = publishedAnnouncement(communityA, "Water advisory", publishedAt);
		when(announcementRepository.findPublishedByIdAndCommunityId(announcement.getId(), communityA))
			.thenReturn(Optional.of(announcement));
		when(attachmentReferenceRepository.findByOwnerTypeAndOwnerId(any(), any())).thenReturn(List.of());

		ResidentAnnouncementResponse response = service.getPublishedAnnouncementForResident(announcement.getId());

		assertThat(response.id()).isEqualTo(announcement.getId());
		assertThat(response.title()).isEqualTo("Water advisory");
		assertThat(response.content()).isEqualTo("Water advisory body");
		assertThat(response.category()).isEqualTo(AnnouncementCategory.ADVISORY);
		assertThat(response.publishedAt()).isEqualTo(publishedAt);
		assertThat(response.attachments()).isEmpty();
	}

	@Test
	void shouldScopeDetailLookupToActiveCommunity() {
		givenResidentWithActiveMembership(communityA);
		UUID anyAnnouncementId = UUID.randomUUID();
		when(announcementRepository.findPublishedByIdAndCommunityId(anyAnnouncementId, communityA))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getPublishedAnnouncementForResident(anyAnnouncementId))
			.isInstanceOf(NotFoundException.class);

		verify(announcementRepository).findPublishedByIdAndCommunityId(anyAnnouncementId, communityA);
		verify(announcementRepository, never()).findById(any());
		verify(announcementRepository, never()).findByIdWithDetails(any());
	}

	@Test
	void shouldReturnTheSameResponseForDraftForeignAndMissingAnnouncements() {
		givenResidentWithActiveMembership(communityA);
		UUID draftId = UUID.randomUUID();
		UUID foreignId = UUID.randomUUID();
		UUID missingId = UUID.randomUUID();
		when(announcementRepository.findPublishedByIdAndCommunityId(any(), eq(communityA)))
			.thenReturn(Optional.empty());

		String draftMessage = messageOf(draftId);
		String foreignMessage = messageOf(foreignId);
		String missingMessage = messageOf(missingId);

		assertThat(draftMessage).isEqualTo(foreignMessage).isEqualTo(missingMessage);
		assertThat(draftMessage).isEqualTo("Announcement not found");
	}

	private String messageOf(UUID announcementId) {
		try {
			service.getPublishedAnnouncementForResident(announcementId);
			throw new AssertionError("Expected NotFoundException");
		} catch (NotFoundException ex) {
			return ex.getMessage();
		}
	}

	@Test
	void shouldRejectDetailForUnauthenticatedRequest() {
		UserContextHolder.clear();

		assertThatThrownBy(() -> service.getPublishedAnnouncementForResident(UUID.randomUUID()))
			.isInstanceOf(ForbiddenException.class);
		verify(announcementRepository, never()).findPublishedByIdAndCommunityId(any(), any());
	}

	@Test
	void shouldRejectDetailWhenMembershipIsInactive() {
		UUID userId = givenAuthenticatedResident(communityA);
		when(userCommunityRepository.findByUserIdAndCommunityIdAndUserStatusActive(userId, communityA))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getPublishedAnnouncementForResident(UUID.randomUUID()))
			.isInstanceOf(ForbiddenException.class);
		verify(announcementRepository, never()).findPublishedByIdAndCommunityId(any(), any());
	}

	@Test
	void shouldRejectDetailWithoutAnnouncementId() {
		givenResidentWithActiveMembership(communityA);

		assertThatThrownBy(() -> service.getPublishedAnnouncementForResident(null))
			.isInstanceOf(ValidationException.class);
		verify(announcementRepository, never()).findPublishedByIdAndCommunityId(any(), any());
	}

	// --- query-level guarantees -------------------------------------------------------------
	// Visibility and ordering are enforced by the repository query, not by response mapping,
	// so the contract of those queries is asserted directly.

	@Test
	void residentListQueryShouldFilterByCommunityAndPublishedStateAndOrderByPublicationDesc() throws Exception {
		String jpql = queryOf("findPublishedByCommunityId", UUID.class, Pageable.class);

		assertThat(jpql).contains("a.community.id = :communityId");
		assertThat(jpql).contains("a.status = com.ria.olita.tech.silingan.entity.AnnouncementStatus.PUBLISHED");
		assertThat(jpql).contains("a.publishedAt IS NOT NULL");
		assertThat(jpql).contains("a.publishAt IS NULL OR a.publishAt <= CURRENT_TIMESTAMP");
		assertThat(jpql).contains("ORDER BY a.publishedAt DESC, a.id DESC");
		assertThat(jpql).doesNotContain("ORDER BY a.createdAt");
		assertThat(jpql).doesNotContain("a.updatedAt");
	}

	@Test
	void residentDetailQueryShouldResolveByIdCommunityAndPublishedStateTogether() throws Exception {
		String jpql = queryOf("findPublishedByIdAndCommunityId", UUID.class, UUID.class);

		assertThat(jpql).contains("a.id = :id");
		assertThat(jpql).contains("a.community.id = :communityId");
		assertThat(jpql).contains("a.status = com.ria.olita.tech.silingan.entity.AnnouncementStatus.PUBLISHED");
		assertThat(jpql).contains("a.publishedAt IS NOT NULL");
	}

	private String queryOf(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
		Query query = AnnouncementRepository.class.getMethod(methodName, parameterTypes).getAnnotation(Query.class);
		assertThat(query).as("%s must be backed by an explicit query", methodName).isNotNull();
		return query.value();
	}

	// --- response shape ---------------------------------------------------------------------

	@Test
	void residentResponseShouldNotExposeStaffOrAuditFields() {
		List<String> components = java.util.Arrays.stream(ResidentAnnouncementResponse.class.getRecordComponents())
			.map(java.lang.reflect.RecordComponent::getName)
			.toList();

		assertThat(components).doesNotContain(
			"status", "createdBy", "updatedBy", "createdAt", "updatedAt", "publishedBy", "communityId", "deleted");
		assertThat(components).contains("id", "title", "content", "publishedAt", "attachments");
	}
}
