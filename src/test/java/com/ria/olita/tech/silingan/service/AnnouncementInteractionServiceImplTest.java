package com.ria.olita.tech.silingan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;

import com.ria.olita.tech.silingan.dto.res.AnnouncementInteractionResponse;
import com.ria.olita.tech.silingan.dto.res.ResidentAnnouncementResponse;
import com.ria.olita.tech.silingan.entity.Announcement;
import com.ria.olita.tech.silingan.entity.AnnouncementCategory;
import com.ria.olita.tech.silingan.entity.AnnouncementStatus;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.mapper.AnnouncementMapperImpl;
import com.ria.olita.tech.silingan.repository.AnnouncementAcknowledgementRepository;
import com.ria.olita.tech.silingan.repository.AnnouncementHelpfulMarkRepository;
import com.ria.olita.tech.silingan.repository.AnnouncementRepository;
import com.ria.olita.tech.silingan.repository.AttachmentReferenceRepository;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.rest.SecuredAnnouncementController;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.service.impl.AnnouncementInteractionRecorder;
import com.ria.olita.tech.silingan.service.impl.AnnouncementServiceImpl;

/**
 * Exercises the resident interaction behaviour against an in-memory store that enforces the same
 * uniqueness rule as the database, so idempotency, concurrency and count correctness are verified
 * rather than mocked away.
 */
class AnnouncementInteractionServiceImplTest {

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

	/** Stand-in for a table whose unique key is (announcement, membership). */
	private final Set<String> acknowledgements = ConcurrentHashMap.newKeySet();
	private final Set<String> helpfulMarks = ConcurrentHashMap.newKeySet();

	private final UUID communityA = UUID.randomUUID();
	private final UUID communityB = UUID.randomUUID();

	AnnouncementInteractionServiceImplTest() {
		wireStore(acknowledgements, true);
		wireStore(helpfulMarks, false);
	}

	@AfterEach
	void tearDown() {
		UserContextHolder.clear();
	}

	// --- in-memory store wiring --------------------------------------------------------------

	private static String key(UUID announcementId, UUID membershipId) {
		return announcementId + "|" + membershipId;
	}

	private void wireStore(Set<String> store, boolean acknowledgement) {
		if (acknowledgement) {
			when(acknowledgementRepository.existsByAnnouncementIdAndMembershipId(any(), any()))
				.thenAnswer(in -> store.contains(key(in.getArgument(0), in.getArgument(1))));
			when(acknowledgementRepository.countByAnnouncementId(any()))
				.thenAnswer(in -> countFor(store, in.getArgument(0)));
			Mockito.doAnswer(in -> insert(store, in.getArgument(0), in.getArgument(1)))
				.when(interactionRecorder).insertAcknowledgement(any(), any());
		} else {
			when(helpfulMarkRepository.existsByAnnouncementIdAndMembershipId(any(), any()))
				.thenAnswer(in -> store.contains(key(in.getArgument(0), in.getArgument(1))));
			when(helpfulMarkRepository.countByAnnouncementId(any()))
				.thenAnswer(in -> countFor(store, in.getArgument(0)));
			Mockito.doAnswer(in -> insert(store, in.getArgument(0), in.getArgument(1)))
				.when(interactionRecorder).insertHelpfulMark(any(), any());
		}
	}

	private static long countFor(Set<String> store, UUID announcementId) {
		return store.stream().filter(entry -> entry.startsWith(announcementId + "|")).count();
	}

	private static Object insert(Set<String> store, Announcement announcement, UserCommunity membership) {
		if (!store.add(key(announcement.getId(), membership.getId()))) {
			throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
		}
		return null;
	}

	// --- fixtures ------------------------------------------------------------------------------

	private UUID givenResident(UUID activeCommunityId) {
		UUID userId = UUID.randomUUID();
		UUID membershipId = UUID.randomUUID();

		UserContextHolder.set(UserContext.builder()
			.userId(userId.toString())
			.communityId(activeCommunityId == null ? null : activeCommunityId.toString())
			.roles(List.of(SilinganRealmRole.RESIDENT))
			.build());

		if (activeCommunityId != null) {
			when(userCommunityRepository.findByUserIdAndCommunityIdAndUserStatusActive(userId, activeCommunityId))
				.thenReturn(Optional.of(UserCommunity.builder()
					.id(membershipId)
					.user(User.builder().id(userId).build())
					.userId(userId)
					.communityId(activeCommunityId)
					.role(SilinganRealmRole.RESIDENT)
					.build()));
		}
		return membershipId;
	}

	private Announcement givenVisibleAnnouncement(UUID communityId) {
		Announcement announcement = Announcement.builder()
			.id(UUID.randomUUID())
			.community(Community.builder().id(communityId).build())
			.communityId(communityId)
			.title("Water advisory")
			.content("No water on Monday")
			.category(AnnouncementCategory.ADVISORY)
			.status(AnnouncementStatus.PUBLISHED)
			.build();
		announcement.setPublishedAt(java.time.LocalDateTime.now());
		when(announcementRepository.findPublishedByIdAndCommunityId(announcement.getId(), communityId))
			.thenReturn(Optional.of(announcement));
		return announcement;
	}

	/** A draft, archived, soft-deleted or foreign announcement: the visibility query returns nothing. */
	private UUID givenInvisibleAnnouncement() {
		UUID announcementId = UUID.randomUUID();
		when(announcementRepository.findPublishedByIdAndCommunityId(Mockito.eq(announcementId), any()))
			.thenReturn(Optional.empty());
		return announcementId;
	}

	// --- acknowledgement -----------------------------------------------------------------------

	@Test
	void shouldAcknowledgePublishedAnnouncement() {
		UUID membershipId = givenResident(communityA);
		Announcement announcement = givenVisibleAnnouncement(communityA);

		AnnouncementInteractionResponse response = service.acknowledgeAnnouncement(announcement.getId());

		assertThat(response.announcementId()).isEqualTo(announcement.getId());
		assertThat(response.acknowledged()).isTrue();
		assertThat(response.helpful()).isFalse();
		assertThat(response.acknowledgementCount()).isEqualTo(1);
		assertThat(response.helpfulCount()).isZero();
		assertThat(acknowledgements).containsExactly(key(announcement.getId(), membershipId));
	}

	@Test
	void shouldRecordAcknowledgementAgainstTheResolvedMembershipAndAnnouncement() {
		UUID membershipId = givenResident(communityA);
		Announcement announcement = givenVisibleAnnouncement(communityA);

		service.acknowledgeAnnouncement(announcement.getId());

		ArgumentCaptors captured = captureAcknowledgement();
		assertThat(captured.announcement().getId()).isEqualTo(announcement.getId());
		assertThat(captured.membership().getId()).isEqualTo(membershipId);
		assertThat(captured.membership().getCommunityId()).isEqualTo(communityA);
	}

	@Test
	void shouldPersistAcknowledgementThroughAnEntityThatCarriesACreationTimestamp() throws Exception {
		assertThat(com.ria.olita.tech.silingan.entity.AnnouncementAcknowledgement.class
			.getDeclaredField("createdAt")
			.getAnnotation(org.hibernate.annotations.CreationTimestamp.class)).isNotNull();
		assertThat(com.ria.olita.tech.silingan.entity.AnnouncementHelpfulMark.class
			.getDeclaredField("createdAt")
			.getAnnotation(org.hibernate.annotations.CreationTimestamp.class)).isNotNull();
	}

	@Test
	void shouldTreatRepeatedAcknowledgementAsSuccessWithoutDuplicatingOrRecounting() {
		givenResident(communityA);
		Announcement announcement = givenVisibleAnnouncement(communityA);

		service.acknowledgeAnnouncement(announcement.getId());
		AnnouncementInteractionResponse repeated = service.acknowledgeAnnouncement(announcement.getId());

		assertThat(repeated.acknowledged()).isTrue();
		assertThat(repeated.acknowledgementCount()).isEqualTo(1);
		assertThat(acknowledgements).hasSize(1);
		verify(interactionRecorder, Mockito.times(1)).insertAcknowledgement(any(), any());
	}

	@Test
	void shouldKeepOnlyOneAcknowledgementUnderConcurrentDuplicateRequests() throws Exception {
		givenResident(communityA);
		Announcement announcement = givenVisibleAnnouncement(communityA);
		forceBothCallersPastTheExistenceCheck(acknowledgementRepository, null);

		List<AnnouncementInteractionResponse> responses = runConcurrently(
			() -> service.acknowledgeAnnouncement(announcement.getId()));

		assertThat(acknowledgements).hasSize(1);
		assertThat(responses).allSatisfy(response -> {
			assertThat(response.acknowledged()).isTrue();
			assertThat(response.acknowledgementCount()).isEqualTo(1);
		});
		verify(interactionRecorder, Mockito.times(2)).insertAcknowledgement(any(), any());
	}

	@Test
	void shouldRejectAcknowledgingAnInvisibleAnnouncement() {
		givenResident(communityA);
		UUID draftOrArchived = givenInvisibleAnnouncement();

		assertThatThrownBy(() -> service.acknowledgeAnnouncement(draftOrArchived))
			.isInstanceOf(NotFoundException.class)
			.hasMessage("Announcement not found");
		assertThat(acknowledgements).isEmpty();
		verify(interactionRecorder, never()).insertAcknowledgement(any(), any());
	}

	@Test
	void shouldRejectAcknowledgingAnotherCommunityAnnouncement() {
		givenResident(communityA);
		Announcement foreign = givenVisibleAnnouncement(communityB);

		assertThatThrownBy(() -> service.acknowledgeAnnouncement(foreign.getId()))
			.isInstanceOf(NotFoundException.class);

		// The visibility query was scoped to the resident's own community, so nothing was found.
		verify(announcementRepository).findPublishedByIdAndCommunityId(foreign.getId(), communityA);
		assertThat(acknowledgements).isEmpty();
		assertThat(countFor(acknowledgements, foreign.getId())).isZero();
	}

	@Test
	void shouldRejectAcknowledgementWhenMembershipIsInactive() {
		UUID userId = UUID.randomUUID();
		UserContextHolder.set(UserContext.builder()
			.userId(userId.toString())
			.communityId(communityA.toString())
			.roles(List.of(SilinganRealmRole.RESIDENT))
			.build());
		when(userCommunityRepository.findByUserIdAndCommunityIdAndUserStatusActive(userId, communityA))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.acknowledgeAnnouncement(UUID.randomUUID()))
			.isInstanceOf(ForbiddenException.class);
		assertThat(acknowledgements).isEmpty();
		verify(interactionRecorder, never()).insertAcknowledgement(any(), any());
	}

	@Test
	void shouldRejectAcknowledgementWithoutAnActiveCommunity() {
		givenResident(null);

		assertThatThrownBy(() -> service.acknowledgeAnnouncement(UUID.randomUUID()))
			.isInstanceOf(ForbiddenException.class);
		assertThat(acknowledgements).isEmpty();
	}

	@Test
	void shouldRejectAcknowledgementForUnauthenticatedRequest() {
		UserContextHolder.clear();

		assertThatThrownBy(() -> service.acknowledgeAnnouncement(UUID.randomUUID()))
			.isInstanceOf(ForbiddenException.class);
		assertThat(acknowledgements).isEmpty();
		verify(announcementRepository, never()).findPublishedByIdAndCommunityId(any(), any());
	}

	@Test
	void shouldRejectAcknowledgementWithoutAnnouncementId() {
		givenResident(communityA);

		assertThatThrownBy(() -> service.acknowledgeAnnouncement(null))
			.isInstanceOf(ValidationException.class);
		assertThat(acknowledgements).isEmpty();
	}

	// --- helpful mark ----------------------------------------------------------------------------

	@Test
	void shouldMarkPublishedAnnouncementAsHelpful() {
		UUID membershipId = givenResident(communityA);
		Announcement announcement = givenVisibleAnnouncement(communityA);

		AnnouncementInteractionResponse response = service.markAnnouncementHelpful(announcement.getId());

		assertThat(response.announcementId()).isEqualTo(announcement.getId());
		assertThat(response.helpful()).isTrue();
		assertThat(response.acknowledged()).isFalse();
		assertThat(response.helpfulCount()).isEqualTo(1);
		assertThat(response.acknowledgementCount()).isZero();
		assertThat(helpfulMarks).containsExactly(key(announcement.getId(), membershipId));
	}

	@Test
	void shouldTreatRepeatedHelpfulRequestAsSuccessWithoutDuplicatingOrRecounting() {
		givenResident(communityA);
		Announcement announcement = givenVisibleAnnouncement(communityA);

		service.markAnnouncementHelpful(announcement.getId());
		AnnouncementInteractionResponse repeated = service.markAnnouncementHelpful(announcement.getId());

		assertThat(repeated.helpful()).isTrue();
		assertThat(repeated.helpfulCount()).isEqualTo(1);
		assertThat(helpfulMarks).hasSize(1);
		verify(interactionRecorder, Mockito.times(1)).insertHelpfulMark(any(), any());
	}

	@Test
	void shouldKeepOnlyOneHelpfulMarkUnderConcurrentDuplicateRequests() throws Exception {
		givenResident(communityA);
		Announcement announcement = givenVisibleAnnouncement(communityA);
		forceBothCallersPastTheExistenceCheck(null, helpfulMarkRepository);

		List<AnnouncementInteractionResponse> responses = runConcurrently(
			() -> service.markAnnouncementHelpful(announcement.getId()));

		assertThat(helpfulMarks).hasSize(1);
		assertThat(responses).allSatisfy(response -> {
			assertThat(response.helpful()).isTrue();
			assertThat(response.helpfulCount()).isEqualTo(1);
		});
	}

	@Test
	void shouldRejectHelpfulMarkOnAnInvisibleAnnouncement() {
		givenResident(communityA);
		UUID draftOrArchived = givenInvisibleAnnouncement();

		assertThatThrownBy(() -> service.markAnnouncementHelpful(draftOrArchived))
			.isInstanceOf(NotFoundException.class)
			.hasMessage("Announcement not found");
		assertThat(helpfulMarks).isEmpty();
		verify(interactionRecorder, never()).insertHelpfulMark(any(), any());
	}

	@Test
	void shouldRejectHelpfulMarkOnAnotherCommunityAnnouncement() {
		givenResident(communityA);
		Announcement foreign = givenVisibleAnnouncement(communityB);

		assertThatThrownBy(() -> service.markAnnouncementHelpful(foreign.getId()))
			.isInstanceOf(NotFoundException.class);
		assertThat(helpfulMarks).isEmpty();
		assertThat(countFor(helpfulMarks, foreign.getId())).isZero();
	}

	@Test
	void shouldRejectHelpfulMarkForUnauthenticatedRequest() {
		UserContextHolder.clear();

		assertThatThrownBy(() -> service.markAnnouncementHelpful(UUID.randomUUID()))
			.isInstanceOf(ForbiddenException.class);
		assertThat(helpfulMarks).isEmpty();
	}

	@Test
	void shouldRejectHelpfulMarkWhenMembershipIsInactive() {
		UUID userId = UUID.randomUUID();
		UserContextHolder.set(UserContext.builder()
			.userId(userId.toString())
			.communityId(communityA.toString())
			.roles(List.of(SilinganRealmRole.RESIDENT))
			.build());
		when(userCommunityRepository.findByUserIdAndCommunityIdAndUserStatusActive(userId, communityA))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.markAnnouncementHelpful(UUID.randomUUID()))
			.isInstanceOf(ForbiddenException.class);
		assertThat(helpfulMarks).isEmpty();
	}

	// --- resident specific status ------------------------------------------------------------------

	@Test
	void shouldKeepInteractionStatusPerResidentWhileSharingTotals() {
		UUID announcementId = UUID.randomUUID();
		Announcement announcement = Announcement.builder()
			.id(announcementId)
			.community(Community.builder().id(communityA).build())
			.communityId(communityA)
			.title("Assembly")
			.content("Saturday 2PM")
			.category(AnnouncementCategory.EVENT)
			.status(AnnouncementStatus.PUBLISHED)
			.build();
		announcement.setPublishedAt(java.time.LocalDateTime.now());
		when(announcementRepository.findPublishedByIdAndCommunityId(announcementId, communityA))
			.thenReturn(Optional.of(announcement));
		when(attachmentReferenceRepository.findByOwnerTypeAndOwnerId(any(), any())).thenReturn(List.of());

		givenResident(communityA);
		AnnouncementInteractionResponse residentA = service.acknowledgeAnnouncement(announcementId);
		service.markAnnouncementHelpful(announcementId);
		assertThat(residentA.acknowledged()).isTrue();

		UserContextHolder.clear();
		givenResident(communityA);
		ResidentAnnouncementResponse seenByResidentB = service.getPublishedAnnouncementForResident(announcementId);

		assertThat(seenByResidentB.acknowledged()).isFalse();
		assertThat(seenByResidentB.helpful()).isFalse();
		assertThat(seenByResidentB.acknowledgementCount()).isEqualTo(1);
		assertThat(seenByResidentB.helpfulCount()).isEqualTo(1);

		// Resident B now acknowledges; totals grow, resident A's record is untouched.
		service.acknowledgeAnnouncement(announcementId);
		assertThat(acknowledgements).hasSize(2);
		assertThat(countFor(acknowledgements, announcementId)).isEqualTo(2);
		assertThat(countFor(helpfulMarks, announcementId)).isEqualTo(1);
	}

	@Test
	void shouldReportZeroCountsWhenNobodyInteracted() {
		givenResident(communityA);
		Announcement announcement = givenVisibleAnnouncement(communityA);
		when(attachmentReferenceRepository.findByOwnerTypeAndOwnerId(any(), any())).thenReturn(List.of());

		ResidentAnnouncementResponse response = service.getPublishedAnnouncementForResident(announcement.getId());

		assertThat(response.acknowledged()).isFalse();
		assertThat(response.helpful()).isFalse();
		assertThat(response.acknowledgementCount()).isZero();
		assertThat(response.helpfulCount()).isZero();
	}

	@Test
	void shouldKeepAcknowledgementAndHelpfulCountsIndependent() {
		givenResident(communityA);
		Announcement announcement = givenVisibleAnnouncement(communityA);

		AnnouncementInteractionResponse afterAcknowledge = service.acknowledgeAnnouncement(announcement.getId());
		assertThat(afterAcknowledge.acknowledgementCount()).isEqualTo(1);
		assertThat(afterAcknowledge.helpfulCount()).isZero();

		AnnouncementInteractionResponse afterHelpful = service.markAnnouncementHelpful(announcement.getId());
		assertThat(afterHelpful.acknowledgementCount()).isEqualTo(1);
		assertThat(afterHelpful.helpfulCount()).isEqualTo(1);
		assertThat(afterHelpful.acknowledged()).isTrue();
		assertThat(afterHelpful.helpful()).isTrue();
	}

	// --- list projection -----------------------------------------------------------------------------

	@Test
	void shouldPopulateResidentListWithBatchedStatusesAndTotals() {
		UUID membershipId = givenResident(communityA);
		Announcement first = givenVisibleAnnouncement(communityA);
		Announcement second = givenVisibleAnnouncement(communityA);

		when(announcementRepository.findPublishedByCommunityId(any(), any()))
			.thenReturn(new PageImpl<>(List.of(first, second)));
		when(attachmentReferenceRepository.findByOwnerTypeAndOwnerIdIn(any(), any())).thenReturn(List.of());
		when(acknowledgementRepository.countByAnnouncementIdIn(any()))
			.thenReturn(List.of(countProjection(first.getId(), 3L)));
		when(helpfulMarkRepository.countByAnnouncementIdIn(any()))
			.thenReturn(List.of(countProjection(second.getId(), 7L)));
		when(acknowledgementRepository.findAnnouncementIdsByMembershipIdAndAnnouncementIdIn(membershipId, List.of(first.getId(), second.getId())))
			.thenReturn(List.of(first.getId()));
		when(helpfulMarkRepository.findAnnouncementIdsByMembershipIdAndAnnouncementIdIn(membershipId, List.of(first.getId(), second.getId())))
			.thenReturn(List.of());

		List<ResidentAnnouncementResponse> page =
			service.getPublishedAnnouncementsForResident(PageRequest.of(0, 10)).getContent();

		assertThat(page.get(0).acknowledged()).isTrue();
		assertThat(page.get(0).helpful()).isFalse();
		assertThat(page.get(0).acknowledgementCount()).isEqualTo(3);
		assertThat(page.get(0).helpfulCount()).isZero();

		assertThat(page.get(1).acknowledged()).isFalse();
		assertThat(page.get(1).acknowledgementCount()).isZero();
		assertThat(page.get(1).helpfulCount()).isEqualTo(7);

		// One grouped/batched query per concern - never one per announcement.
		verify(acknowledgementRepository, Mockito.times(1)).countByAnnouncementIdIn(any());
		verify(helpfulMarkRepository, Mockito.times(1)).countByAnnouncementIdIn(any());
		verify(acknowledgementRepository, Mockito.times(1)).findAnnouncementIdsByMembershipIdAndAnnouncementIdIn(any(), any());
		verify(helpfulMarkRepository, Mockito.times(1)).findAnnouncementIdsByMembershipIdAndAnnouncementIdIn(any(), any());
		verify(acknowledgementRepository, never()).countByAnnouncementId(any());
		verify(helpfulMarkRepository, never()).countByAnnouncementId(any());
	}

	// --- endpoint protection ---------------------------------------------------------------------------

	@Test
	void residentInteractionEndpointsShouldRequireAnAuthenticatedResident() {
		PreAuthorize preAuthorize = SecuredAnnouncementController.class.getAnnotation(PreAuthorize.class);

		assertThat(preAuthorize).isNotNull();
		assertThat(preAuthorize.value()).contains("isAuthenticated()").contains("hasRole('RESIDENT')");
	}

	// --- helpers -----------------------------------------------------------------------------------------

	private record ArgumentCaptors(Announcement announcement, UserCommunity membership) {
	}

	private ArgumentCaptors captureAcknowledgement() {
		var announcementCaptor = org.mockito.ArgumentCaptor.forClass(Announcement.class);
		var membershipCaptor = org.mockito.ArgumentCaptor.forClass(UserCommunity.class);
		verify(interactionRecorder).insertAcknowledgement(announcementCaptor.capture(), membershipCaptor.capture());
		return new ArgumentCaptors(announcementCaptor.getValue(), membershipCaptor.getValue());
	}

	private static com.ria.olita.tech.silingan.repository.AnnouncementInteractionCount countProjection(UUID announcementId, long total) {
		return new com.ria.olita.tech.silingan.repository.AnnouncementInteractionCount() {
			@Override
			public UUID getAnnouncementId() {
				return announcementId;
			}

			@Override
			public long getTotal() {
				return total;
			}
		};
	}

	/**
	 * Makes the first existence check of both concurrent callers happen before either insert, which
	 * is the interleaving that a plain check-then-insert would get wrong.
	 */
	private void forceBothCallersPastTheExistenceCheck(
		AnnouncementAcknowledgementRepository ackRepo,
		AnnouncementHelpfulMarkRepository helpfulRepo
	) {
		CyclicBarrier barrier = new CyclicBarrier(2);
		AtomicInteger calls = new AtomicInteger();
		Set<String> store = ackRepo != null ? acknowledgements : helpfulMarks;

		var answer = (org.mockito.stubbing.Answer<Boolean>) invocation -> {
			if (calls.incrementAndGet() <= 2) {
				barrier.await(5, TimeUnit.SECONDS);
				return false;
			}
			return store.contains(key(invocation.getArgument(0), invocation.getArgument(1)));
		};

		if (ackRepo != null) {
			when(ackRepo.existsByAnnouncementIdAndMembershipId(any(), any())).thenAnswer(answer);
		} else {
			when(helpfulRepo.existsByAnnouncementIdAndMembershipId(any(), any())).thenAnswer(answer);
		}
	}

	private List<AnnouncementInteractionResponse> runConcurrently(
		java.util.function.Supplier<AnnouncementInteractionResponse> action
	) throws Exception {
		UserContext context = UserContextHolder.get();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch done = new CountDownLatch(2);
		List<AnnouncementInteractionResponse> responses = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
		List<Throwable> failures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

		try {
			for (int i = 0; i < 2; i++) {
				pool.submit(() -> {
					UserContextHolder.set(context);
					try {
						responses.add(action.get());
					} catch (Throwable ex) {
						failures.add(ex);
					} finally {
						UserContextHolder.clear();
						done.countDown();
					}
				});
			}
			assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
		} finally {
			pool.shutdownNow();
		}

		assertThat(failures).as("concurrent duplicates must not surface an error").isEmpty();
		assertThat(responses).hasSize(2);
		return responses;
	}
}
