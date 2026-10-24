package com.ria.olita.tech.silingan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.ria.olita.tech.silingan.dto.req.AnnouncementFilterCriteria;
import com.ria.olita.tech.silingan.dto.req.CreateAnnouncementRequest;
import com.ria.olita.tech.silingan.dto.req.UpdateAnnouncementRequest;
import com.ria.olita.tech.silingan.dto.res.AnnouncementResponse;
import com.ria.olita.tech.silingan.entity.Announcement;
import com.ria.olita.tech.silingan.entity.AnnouncementCategory;
import com.ria.olita.tech.silingan.entity.AnnouncementStatus;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.rbac.CommunityAccess;
import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
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

class AnnouncementServiceImplTest {

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

	@BeforeEach
	void setUp() {
		when(attachmentReferenceRepository.findByOwnerTypeAndOwnerId(any(), any())).thenReturn(List.of());
	}

	@AfterEach
	void tearDown() {
		UserContextHolder.clear();
	}

	// --- fixtures -------------------------------------------------------------------------

	private User givenCaller(UUID communityId, SilinganRealmRole realmRole, PermissionEnum... permissions) {
		UUID userId = UUID.randomUUID();
		User user = User.builder().id(userId).username("staff").build();

		UserContextHolder.set(UserContext.builder()
			.userId(userId.toString())
			.communityId(communityId == null ? null : communityId.toString())
			.roles(List.of(realmRole))
			.communityAccess(communityId == null ? null : new CommunityAccess(communityId, List.of(permissions)))
			.build());

		when(userRepository.findById(userId)).thenReturn(Optional.of(user));

		if (communityId != null) {
			UserCommunity membership = UserCommunity.builder()
				.user(user)
				.userId(userId)
				.communityId(communityId)
				.role(realmRole)
				.build();
			when(userCommunityRepository.findByUserIdAndCommunityIdAndUserStatusActive(userId, communityId))
				.thenReturn(Optional.of(membership));
		}

		return user;
	}

	private User givenCommunityAdmin(UUID communityId) {
		return givenCaller(communityId, SilinganRealmRole.COMMUNITY_ADMIN);
	}

	private User givenPmoStaff(UUID communityId) {
		return givenCaller(communityId, SilinganRealmRole.STAFF,
			PermissionEnum.ANNOUNCEMENT_VIEW, PermissionEnum.ANNOUNCEMENT_MANAGE);
	}

	private void givenActiveCommunity(UUID communityId) {
		when(communityRepository.findByIdAndStatus(communityId, CommunityStatus.ACTIVE))
			.thenReturn(Optional.of(Community.builder().id(communityId).build()));
	}

	private Announcement givenAnnouncement(UUID communityId, AnnouncementStatus status) {
		Announcement announcement = Announcement.builder()
			.id(UUID.randomUUID())
			.community(Community.builder().id(communityId).build())
			.communityId(communityId)
			.title("Original title")
			.content("Original content")
			.category(AnnouncementCategory.GENERAL)
			.status(status)
			.build();
		announcement.setCreatedAt(LocalDateTime.now().minusDays(3));
		when(announcementRepository.findById(announcement.getId())).thenReturn(Optional.of(announcement));
		when(announcementRepository.findByIdWithDetails(announcement.getId())).thenReturn(Optional.of(announcement));
		return announcement;
	}

	private void givenSaveEchoesEntity() {
		when(announcementRepository.save(any(Announcement.class))).thenAnswer(invocation -> {
			Announcement announcement = invocation.getArgument(0);
			if (announcement.getId() == null) {
				announcement.setId(UUID.randomUUID());
			}
			return announcement;
		});
	}

	private CreateAnnouncementRequest createRequest(UUID communityId, String title, String content) {
		return CreateAnnouncementRequest.builder()
			.communityId(communityId)
			.title(title)
			.content(content)
			.category(AnnouncementCategory.ADVISORY)
			.pinned(false)
			.build();
	}

	// --- create ---------------------------------------------------------------------------

	@Test
	void shouldLetCommunityAdminCreateDraftAnnouncementForOwnCommunity() {
		givenCommunityAdmin(communityA);
		givenActiveCommunity(communityA);
		givenSaveEchoesEntity();

		AnnouncementResponse response = service.createAnnouncement(createRequest(communityA, "Water interruption", "No water on Monday"));

		assertThat(response.id()).isNotNull();
		assertThat(response.communityId()).isEqualTo(communityA);
		assertThat(response.status()).isEqualTo(AnnouncementStatus.DRAFT);
		assertThat(response.title()).isEqualTo("Water interruption");
	}

	@Test
	void shouldLetPmoStaffWithManagePermissionCreateAnnouncement() {
		givenPmoStaff(communityA);
		givenActiveCommunity(communityA);
		givenSaveEchoesEntity();

		AnnouncementResponse response = service.createAnnouncement(createRequest(communityA, "Brownout", "Scheduled brownout"));

		assertThat(response.communityId()).isEqualTo(communityA);
		assertThat(response.status()).isEqualTo(AnnouncementStatus.DRAFT);
	}

	@Test
	void shouldRejectBlankTitleOnCreate() {
		givenCommunityAdmin(communityA);
		givenActiveCommunity(communityA);

		assertThatThrownBy(() -> service.createAnnouncement(createRequest(communityA, "   ", "Body")))
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("Title");
		verify(announcementRepository, never()).save(any());
	}

	@Test
	void shouldRejectBlankDescriptionOnCreate() {
		givenCommunityAdmin(communityA);
		givenActiveCommunity(communityA);

		assertThatThrownBy(() -> service.createAnnouncement(createRequest(communityA, "Title", "  ")))
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("Description");
		verify(announcementRepository, never()).save(any());
	}

	@Test
	void shouldRejectCreateForUnauthorizedRole() {
		givenCaller(communityA, SilinganRealmRole.RESIDENT);
		givenActiveCommunity(communityA);

		assertThatThrownBy(() -> service.createAnnouncement(createRequest(communityA, "Title", "Body")))
			.isInstanceOf(ForbiddenException.class);
		verify(announcementRepository, never()).save(any());
	}

	@Test
	void shouldRejectCreateForAnotherCommunity() {
		givenCommunityAdmin(communityA);
		givenActiveCommunity(communityB);

		assertThatThrownBy(() -> service.createAnnouncement(createRequest(communityB, "Title", "Body")))
			.isInstanceOf(ForbiddenException.class)
			.hasMessageContaining("do not have access to this community");
		verify(announcementRepository, never()).save(any());
	}

	@Test
	void shouldRejectCreateWhenMembershipIsNotActive() {
		UUID userId = UUID.randomUUID();
		UserContextHolder.set(UserContext.builder()
			.userId(userId.toString())
			.communityId(communityA.toString())
			.roles(List.of(SilinganRealmRole.COMMUNITY_ADMIN))
			.communityAccess(new CommunityAccess(communityA, List.of()))
			.build());
		givenActiveCommunity(communityA);
		when(userCommunityRepository.findByUserIdAndCommunityIdAndUserStatusActive(userId, communityA))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.createAnnouncement(createRequest(communityA, "Title", "Body")))
			.isInstanceOf(ForbiddenException.class);
		verify(announcementRepository, never()).save(any());
	}

	@Test
	void shouldRejectCreateForMissingCommunity() {
		givenCommunityAdmin(communityA);
		when(communityRepository.findByIdAndStatus(communityA, CommunityStatus.ACTIVE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.createAnnouncement(createRequest(communityA, "Title", "Body")))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void shouldIgnoreClientSuppliedServerManagedValuesOnCreate() {
		givenCommunityAdmin(communityA);
		givenActiveCommunity(communityA);
		givenSaveEchoesEntity();

		AnnouncementResponse response = service.createAnnouncement(createRequest(communityA, "Title", "Body"));

		assertThat(response.status()).isEqualTo(AnnouncementStatus.DRAFT);
		assertThat(response.publishedAt()).isNull();
		assertThat(response.publishedBy()).isNull();
		assertThat(response.publishAt()).isNull();
	}

	// --- view -----------------------------------------------------------------------------

	@Test
	void shouldListAnnouncementsScopedToOwnCommunity() {
		givenCommunityAdmin(communityA);
		givenActiveCommunity(communityA);
		Announcement own = givenAnnouncement(communityA, AnnouncementStatus.PUBLISHED);
		when(announcementRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(own)));

		Page<AnnouncementResponse> page = service.getCommunityAnnouncementsWithFilters(
			communityA, AnnouncementFilterCriteria.builder().build(), PageRequest.of(0, 10));

		assertThat(page.getContent()).hasSize(1);
		assertThat(page.getContent().getFirst().communityId()).isEqualTo(communityA);
	}

	@Test
	void shouldApplyNewestFirstDefaultSortWhenPageableIsUnsorted() {
		givenCommunityAdmin(communityA);
		givenActiveCommunity(communityA);
		when(announcementRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of()));

		service.getCommunityAnnouncementsWithFilters(communityA, null, PageRequest.of(0, 10));

		Mockito.verify(announcementRepository).findAll(any(Specification.class), Mockito.argThat(
			(Pageable pageable) -> pageable.getSort().getOrderFor("createdAt") != null
				&& pageable.getSort().getOrderFor("createdAt").isDescending()));
	}

	@Test
	void shouldRejectListingAnotherCommunity() {
		givenCommunityAdmin(communityA);
		givenActiveCommunity(communityB);

		assertThatThrownBy(() -> service.getCommunityAnnouncementsWithFilters(
			communityB, AnnouncementFilterCriteria.builder().build(), PageRequest.of(0, 10)))
			.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void shouldRequireCommunityIdForNonPlatformAdminListing() {
		givenCommunityAdmin(communityA);

		assertThatThrownBy(() -> service.getCommunityAnnouncementsWithFilters(
			null, AnnouncementFilterCriteria.builder().build(), PageRequest.of(0, 10)))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void shouldReturnDetailsOfOwnCommunityAnnouncement() {
		givenCommunityAdmin(communityA);
		Announcement announcement = givenAnnouncement(communityA, AnnouncementStatus.DRAFT);

		AnnouncementResponse response = service.getAnnouncementById(announcement.getId());

		assertThat(response.id()).isEqualTo(announcement.getId());
		assertThat(response.communityId()).isEqualTo(communityA);
	}

	@Test
	void shouldRejectDetailsOfAnotherCommunityAnnouncement() {
		givenCommunityAdmin(communityA);
		Announcement foreign = givenAnnouncement(communityB, AnnouncementStatus.PUBLISHED);

		assertThatThrownBy(() -> service.getAnnouncementById(foreign.getId()))
			.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void shouldReturnNotFoundForMissingAnnouncement() {
		givenCommunityAdmin(communityA);
		UUID missing = UUID.randomUUID();
		when(announcementRepository.findByIdWithDetails(missing)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getAnnouncementById(missing))
			.isInstanceOf(NotFoundException.class);
	}

	// --- update ---------------------------------------------------------------------------

	@Test
	void shouldUpdateDraftAnnouncementFields() {
		givenCommunityAdmin(communityA);
		Announcement draft = givenAnnouncement(communityA, AnnouncementStatus.DRAFT);
		givenSaveEchoesEntity();

		AnnouncementResponse response = service.updateAnnouncement(draft.getId(), UpdateAnnouncementRequest.builder()
			.title("Updated title")
			.content("Updated content")
			.category(AnnouncementCategory.EVENT)
			.pinned(true)
			.build());

		assertThat(response.title()).isEqualTo("Updated title");
		assertThat(response.content()).isEqualTo("Updated content");
		assertThat(response.category()).isEqualTo(AnnouncementCategory.EVENT);
		assertThat(response.pinned()).isTrue();
	}

	@Test
	void shouldNotChangeCommunityOwnershipOnUpdate() {
		givenCommunityAdmin(communityA);
		Announcement draft = givenAnnouncement(communityA, AnnouncementStatus.DRAFT);
		givenSaveEchoesEntity();

		AnnouncementResponse response = service.updateAnnouncement(draft.getId(),
			UpdateAnnouncementRequest.builder().title("Updated").build());

		assertThat(response.communityId()).isEqualTo(communityA);
		assertThat(draft.getCommunity().getId()).isEqualTo(communityA);
	}

	@Test
	void shouldRejectUpdateOfPublishedAnnouncement() {
		givenCommunityAdmin(communityA);
		Announcement published = givenAnnouncement(communityA, AnnouncementStatus.PUBLISHED);

		assertThatThrownBy(() -> service.updateAnnouncement(published.getId(),
			UpdateAnnouncementRequest.builder().title("Updated").build()))
			.isInstanceOf(ValidationException.class);
		assertThat(published.getTitle()).isEqualTo("Original title");
		verify(announcementRepository, never()).save(any());
	}

	@Test
	void shouldRejectBlankTitleOnUpdate() {
		givenCommunityAdmin(communityA);
		Announcement draft = givenAnnouncement(communityA, AnnouncementStatus.DRAFT);

		assertThatThrownBy(() -> service.updateAnnouncement(draft.getId(),
			UpdateAnnouncementRequest.builder().title("   ").build()))
			.isInstanceOf(ValidationException.class);
		verify(announcementRepository, never()).save(any());
	}

	@Test
	void shouldRejectUpdateByUnauthorizedRole() {
		givenCaller(communityA, SilinganRealmRole.RESIDENT);
		Announcement draft = givenAnnouncement(communityA, AnnouncementStatus.DRAFT);

		assertThatThrownBy(() -> service.updateAnnouncement(draft.getId(),
			UpdateAnnouncementRequest.builder().title("Updated").build()))
			.isInstanceOf(ForbiddenException.class);
		verify(announcementRepository, never()).save(any());
	}

	@Test
	void shouldRejectUpdateOfAnotherCommunityAnnouncement() {
		givenCommunityAdmin(communityA);
		Announcement foreign = givenAnnouncement(communityB, AnnouncementStatus.DRAFT);

		assertThatThrownBy(() -> service.updateAnnouncement(foreign.getId(),
			UpdateAnnouncementRequest.builder().title("Hijacked").build()))
			.isInstanceOf(ForbiddenException.class);
		assertThat(foreign.getTitle()).isEqualTo("Original title");
		verify(announcementRepository, never()).save(any());
	}

	// --- publish --------------------------------------------------------------------------

	@Test
	void shouldPublishDraftAnnouncement() {
		User admin = givenCommunityAdmin(communityA);
		Announcement draft = givenAnnouncement(communityA, AnnouncementStatus.DRAFT);
		LocalDateTime createdAt = draft.getCreatedAt();
		givenSaveEchoesEntity();

		AnnouncementResponse response = service.publishAnnouncement(draft.getId());

		assertThat(response.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
		assertThat(response.publishedAt()).isNotNull();
		assertThat(response.publishedBy()).isEqualTo(admin.getId());
		assertThat(response.createdAt()).isEqualTo(createdAt);
	}

	@Test
	void shouldTreatRepeatedPublishAsIdempotent() {
		givenCommunityAdmin(communityA);
		Announcement published = givenAnnouncement(communityA, AnnouncementStatus.PUBLISHED);
		LocalDateTime originalPublishedAt = LocalDateTime.now().minusDays(1);
		published.setPublishedAt(originalPublishedAt);

		AnnouncementResponse response = service.publishAnnouncement(published.getId());

		assertThat(response.status()).isEqualTo(AnnouncementStatus.PUBLISHED);
		assertThat(response.publishedAt()).isEqualTo(originalPublishedAt);
		verify(announcementRepository, never()).save(any());
	}

	@Test
	void shouldRejectPublishFromUnsupportedState() {
		givenCommunityAdmin(communityA);
		Announcement archived = givenAnnouncement(communityA, AnnouncementStatus.ARCHIVED);

		assertThatThrownBy(() -> service.publishAnnouncement(archived.getId()))
			.isInstanceOf(ValidationException.class);
		verify(announcementRepository, never()).save(any());
	}

	@Test
	void shouldRejectPublishByUnauthorizedRole() {
		givenCaller(communityA, SilinganRealmRole.RESIDENT);
		Announcement draft = givenAnnouncement(communityA, AnnouncementStatus.DRAFT);

		assertThatThrownBy(() -> service.publishAnnouncement(draft.getId()))
			.isInstanceOf(ForbiddenException.class);
		assertThat(draft.getStatus()).isEqualTo(AnnouncementStatus.DRAFT);
		verify(announcementRepository, never()).save(any());
	}

	@Test
	void shouldRejectPublishOfAnotherCommunityAnnouncement() {
		givenCommunityAdmin(communityA);
		Announcement foreign = givenAnnouncement(communityB, AnnouncementStatus.DRAFT);

		assertThatThrownBy(() -> service.publishAnnouncement(foreign.getId()))
			.isInstanceOf(ForbiddenException.class);
		assertThat(foreign.getStatus()).isEqualTo(AnnouncementStatus.DRAFT);
		verify(announcementRepository, never()).save(any());
	}

	// --- delete ---------------------------------------------------------------------------

	@Test
	void shouldSoftDeleteDraftAnnouncement() {
		givenCommunityAdmin(communityA);
		Announcement draft = givenAnnouncement(communityA, AnnouncementStatus.DRAFT);
		givenSaveEchoesEntity();

		service.deleteAnnouncement(draft.getId());

		assertThat(draft.getDeleted()).isTrue();
	}

	@Test
	void shouldRejectDeleteOfPublishedAnnouncement() {
		givenCommunityAdmin(communityA);
		Announcement published = givenAnnouncement(communityA, AnnouncementStatus.PUBLISHED);

		assertThatThrownBy(() -> service.deleteAnnouncement(published.getId()))
			.isInstanceOf(ValidationException.class);
		assertThat(published.getDeleted()).isFalse();
		verify(announcementRepository, never()).save(any());
	}

	@Test
	void shouldRejectDeleteByUnauthorizedRole() {
		givenCaller(communityA, SilinganRealmRole.RESIDENT);
		Announcement draft = givenAnnouncement(communityA, AnnouncementStatus.DRAFT);

		assertThatThrownBy(() -> service.deleteAnnouncement(draft.getId()))
			.isInstanceOf(ForbiddenException.class);
		assertThat(draft.getDeleted()).isFalse();
		verify(announcementRepository, never()).save(any());
	}

	@Test
	void shouldRejectDeleteOfAnotherCommunityAnnouncement() {
		givenCommunityAdmin(communityA);
		Announcement foreign = givenAnnouncement(communityB, AnnouncementStatus.DRAFT);

		assertThatThrownBy(() -> service.deleteAnnouncement(foreign.getId()))
			.isInstanceOf(ForbiddenException.class);
		assertThat(foreign.getDeleted()).isFalse();
		verify(announcementRepository, never()).save(any());
	}

	@Test
	void shouldReturnNotFoundWhenDeletingMissingAnnouncement() {
		givenCommunityAdmin(communityA);
		UUID missing = UUID.randomUUID();
		when(announcementRepository.findById(missing)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.deleteAnnouncement(missing))
			.isInstanceOf(NotFoundException.class);
	}
}
