package com.ria.olita.tech.silingan.service.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.ria.olita.tech.silingan.config.OnboardingProperties;
import com.ria.olita.tech.silingan.dto.req.CreateCommunityJoinInvitationRequest;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinInvitationCreatedResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinInvitationPreviewResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinInvitationResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityJoinInvitation;
import com.ria.olita.tech.silingan.entity.CommunityJoinInvitationStatus;
import com.ria.olita.tech.silingan.entity.CommunityJoinInvitationType;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.InvitationDistributionChannel;
import com.ria.olita.tech.silingan.entity.Media;
import com.ria.olita.tech.silingan.exception.ConflictException;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.repository.CommunityJoinInvitationRepository;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.security.scope.CommunityScopeGuard;
import com.ria.olita.tech.silingan.service.CommunityService;
import com.ria.olita.tech.silingan.service.qr.CommunityJoinUrlFactory;
import com.ria.olita.tech.silingan.service.qr.QrCodeGenerator;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommunityJoinInvitationServiceImplTest {

	private static final UUID COMMUNITY_ID = UUID.fromString("5ee38760-cefd-4f0a-a8d2-4941c77fc792");
	private static final UUID OTHER_COMMUNITY_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
	private static final UUID INVITATION_ID = UUID.fromString("c5931ff1-9bce-41a4-93ff-508a674b8611");
	private static final String COMMUNITY_CODE = "MAPLERES-2026";
	private static final String RAW_TOKEN = "Y8hVfM3wQk7xP2nR6cT9aB1cD2eF3gH4iJ5kL6mN7oP";
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 10, 12, 0);
	private static final byte[] PNG = {1, 2, 3, 4};

	@Mock
	private CommunityJoinInvitationRepository invitationRepository;
	@Mock
	private CommunityRepository communityRepository;
	@Mock
	private UserRepository userRepository;
	@Mock
	private CommunityService communityService;
	@Mock
	private CommunityScopeGuard communityScopeGuard;
	@Mock
	private QrCodeGenerator qrCodeGenerator;

	private CommunityJoinInvitationServiceImpl service;
	private InvitationTokenHasher tokenHasher;

	@BeforeEach
	void setUp() {
		tokenHasher = new InvitationTokenHasher();

		OnboardingProperties properties = new OnboardingProperties();
		properties.setJoinBaseUrl("https://app.silingan.ph/join");

		Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

		service = new CommunityJoinInvitationServiceImpl(
			invitationRepository,
			communityRepository,
			userRepository,
			communityService,
			communityScopeGuard,
			new CommunityJoinUrlFactory(properties),
			qrCodeGenerator,
			new SecureInvitationTokenGenerator(),
			tokenHasher,
			properties,
			clock
		);

		when(qrCodeGenerator.generatePng(anyString())).thenReturn(PNG);
	}

	private static Community activeCommunity() {
		Community community = new Community();
		community.setId(COMMUNITY_ID);
		community.setName("Maple Residences");
		community.setCommunityCode(COMMUNITY_CODE);
		community.setStatus(CommunityStatus.ACTIVE);
		return community;
	}

	private static CommunityResponse communityResponse() {
		return CommunityResponse.builder()
			.id(COMMUNITY_ID)
			.communityCode(COMMUNITY_CODE)
			.name("Maple Residences")
			.status(CommunityStatus.ACTIVE)
			.build();
	}

	private CommunityJoinInvitation storedInvitation(
		CommunityJoinInvitationStatus status, LocalDateTime expiresAt, Integer maxUses, int usedCount
	) {
		CommunityJoinInvitation invitation = CommunityJoinInvitation.builder()
			.community(activeCommunity())
			.tokenHash(tokenHasher.hash(RAW_TOKEN))
			.invitationType(CommunityJoinInvitationType.COMMUNITY_BATCH)
			.status(status)
			.expiresAt(expiresAt)
			.maxUses(maxUses)
			.usedCount(usedCount)
			.build();
		invitation.setId(INVITATION_ID);
		return invitation;
	}

	private void givenManageableCommunity() {
		when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(activeCommunity()));
	}

	private void givenStoredInvitation(CommunityJoinInvitation invitation) {
		when(invitationRepository.findByTokenHash(tokenHasher.hash(RAW_TOKEN))).thenReturn(Optional.of(invitation));
	}

	// ---------- create ----------

	@Test
	void createAppliesConfiguredDefaults() {
		givenManageableCommunity();

		service.create(COMMUNITY_ID, CreateCommunityJoinInvitationRequest.builder().build());

		CommunityJoinInvitation saved = captureSaved();
		assertThat(saved.getMaxUses()).isEqualTo(100);
		assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusHours(168));
		assertThat(saved.getUsedCount()).isZero();
		assertThat(saved.getStatus()).isEqualTo(CommunityJoinInvitationStatus.ACTIVE);
		assertThat(saved.getInvitationType()).isEqualTo(CommunityJoinInvitationType.COMMUNITY_BATCH);
	}

	@Test
	void createStoresOnlyTheTokenHashAndReturnsTheRawTokenOnce() {
		givenManageableCommunity();

		CommunityJoinInvitationCreatedResponse response =
			service.create(COMMUNITY_ID, CreateCommunityJoinInvitationRequest.builder().build());

		String rawToken = response.joinUrl().substring(response.joinUrl().indexOf("token=") + "token=".length());
		CommunityJoinInvitation saved = captureSaved();

		assertThat(saved.getTokenHash())
			.isEqualTo(tokenHasher.hash(rawToken))
			.doesNotContain(rawToken);
		assertThat(response.joinUrl()).startsWith("https://app.silingan.ph/join?token=");
	}

	@Test
	void createEncodesTheJoinUrlInTheQrImage() {
		givenManageableCommunity();

		CommunityJoinInvitationCreatedResponse response =
			service.create(COMMUNITY_ID, CreateCommunityJoinInvitationRequest.builder().build());

		verify(qrCodeGenerator).generatePng(response.joinUrl());
		assertThat(response.qrCode().contentType()).isEqualTo("image/png");
		assertThat(response.qrCode().base64()).isEqualTo(Base64.getEncoder().encodeToString(PNG));
		assertThat(response.qrCode().fileName()).isEqualTo("silingan-maple-residences-join.png");
	}

	@Test
	void createNeverExposesTheCommunityCodeOrTokenHash() {
		givenManageableCommunity();

		CommunityJoinInvitationCreatedResponse response =
			service.create(COMMUNITY_ID, CreateCommunityJoinInvitationRequest.builder().build());

		assertThat(response.invitation().toString()).doesNotContain(COMMUNITY_CODE);
		assertThat(response.joinUrl()).doesNotContain(COMMUNITY_CODE);
	}

	@Test
	void createHonoursAdministratorSuppliedLimits() {
		givenManageableCommunity();

		service.create(COMMUNITY_ID, CreateCommunityJoinInvitationRequest.builder()
			.label("Tower A")
			.distributionChannel(InvitationDistributionChannel.VIBER_GC)
			.expiresInHours(24)
			.maxUses(10)
			.build());

		CommunityJoinInvitation saved = captureSaved();
		assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusHours(24));
		assertThat(saved.getMaxUses()).isEqualTo(10);
		assertThat(saved.getLabel()).isEqualTo("Tower A");
		assertThat(saved.getDistributionChannel()).isEqualTo(InvitationDistributionChannel.VIBER_GC);
	}

	@Test
	void createRejectsLimitsOutsideTheSafeBounds() {
		givenManageableCommunity();

		assertThatThrownBy(() -> service.create(COMMUNITY_ID,
			CreateCommunityJoinInvitationRequest.builder().expiresInHours(721).build()))
			.isInstanceOf(ValidationException.class);

		assertThatThrownBy(() -> service.create(COMMUNITY_ID,
			CreateCommunityJoinInvitationRequest.builder().maxUses(501).build()))
			.isInstanceOf(ValidationException.class);

		verify(invitationRepository, never()).saveAndFlush(any());
	}

	@Test
	void createChecksCommunityScopeBeforeReadingTheCommunity() {
		doThrowOnScopeCheck();

		assertThatThrownBy(() -> service.create(COMMUNITY_ID, CreateCommunityJoinInvitationRequest.builder().build()))
			.isInstanceOf(ForbiddenException.class);

		verifyNoInteractions(communityRepository, invitationRepository);
	}

	@Test
	void createRejectsACommunityThatIsNotAcceptingResidents() {
		Community archived = activeCommunity();
		archived.setStatus(CommunityStatus.ARCHIVE);
		when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(archived));

		assertThatThrownBy(() -> service.create(COMMUNITY_ID, CreateCommunityJoinInvitationRequest.builder().build()))
			.isInstanceOf(ConflictException.class);
	}

	@Test
	void createRejectsAnUnknownCommunity() {
		when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(COMMUNITY_ID, CreateCommunityJoinInvitationRequest.builder().build()))
			.isInstanceOf(NotFoundException.class);
	}

	// ---------- preview ----------

	@Test
	void previewResolvesTheCommunityWithoutConsumingAUse() {
		givenStoredInvitation(storedInvitation(CommunityJoinInvitationStatus.ACTIVE, NOW.plusDays(1), 100, 3));
		when(communityService.getByCode(COMMUNITY_CODE)).thenReturn(communityResponse());

		CommunityJoinInvitationPreviewResponse response = service.preview(RAW_TOKEN);

		assertThat(response.communityId()).isEqualTo(COMMUNITY_ID);
		assertThat(response.communityName()).isEqualTo("Maple Residences");
		assertThat(response.invitationId()).isEqualTo(INVITATION_ID);
		verify(invitationRepository, never()).consumeOneUse(any(), any(), any(), any());
		verify(invitationRepository, never()).saveAndFlush(any());
	}

	@Test
	void previewIncludesTheCommunityLogoWhenPresent() {
		CommunityJoinInvitation invitation = storedInvitation(CommunityJoinInvitationStatus.ACTIVE, NOW.plusDays(1), 100, 0);
		Media logo = new Media();
		logo.setUrl("https://cdn.silingan.ph/logo.png");
		invitation.getCommunity().setLogo(logo);
		givenStoredInvitation(invitation);
		when(communityService.getByCode(COMMUNITY_CODE)).thenReturn(communityResponse());

		assertThat(service.preview(RAW_TOKEN).communityLogoUrl()).isEqualTo("https://cdn.silingan.ph/logo.png");
	}

	@Test
	void previewNeverLeaksTheCommunityCodeOrUsageStatistics() {
		givenStoredInvitation(storedInvitation(CommunityJoinInvitationStatus.ACTIVE, NOW.plusDays(1), 100, 7));
		when(communityService.getByCode(COMMUNITY_CODE)).thenReturn(communityResponse());

		assertThat(service.preview(RAW_TOKEN).toString())
			.doesNotContain(COMMUNITY_CODE)
			.doesNotContain(RAW_TOKEN)
			.doesNotContain("usedCount");
	}

	@Test
	void previewRejectsAMalformedTokenWithoutQueryingTheDatabase() {
		assertThatThrownBy(() -> service.preview("short"))
			.isInstanceOf(NotFoundException.class)
			.hasMessage(CommunityJoinInvitationServiceImpl.INVALID_INVITATION_MESSAGE);

		verify(invitationRepository, never()).findByTokenHash(anyString());
	}

	@Test
	void previewRejectsRevokedExpiredAndExhaustedInvitationsIdentically() {
		List<CommunityJoinInvitation> rejected = List.of(
			storedInvitation(CommunityJoinInvitationStatus.REVOKED, NOW.plusDays(1), 100, 0),
			storedInvitation(CommunityJoinInvitationStatus.ACTIVE, NOW.minusMinutes(1), 100, 0),
			storedInvitation(CommunityJoinInvitationStatus.ACTIVE, NOW.plusDays(1), 2, 2)
		);

		for (CommunityJoinInvitation invitation : rejected) {
			givenStoredInvitation(invitation);

			assertThatThrownBy(() -> service.preview(RAW_TOKEN))
				.isInstanceOf(NotFoundException.class)
				.hasMessage(CommunityJoinInvitationServiceImpl.INVALID_INVITATION_MESSAGE);
		}
	}

	@Test
	void previewRejectsAnUnknownTokenWithTheSameMessage() {
		when(invitationRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.preview(RAW_TOKEN))
			.isInstanceOf(NotFoundException.class)
			.hasMessage(CommunityJoinInvitationServiceImpl.INVALID_INVITATION_MESSAGE);
	}

	@Test
	void previewRejectsAnInvitationWhoseCommunityNoLongerAcceptsResidents() {
		givenStoredInvitation(storedInvitation(CommunityJoinInvitationStatus.ACTIVE, NOW.plusDays(1), 100, 0));
		when(communityService.getByCode(COMMUNITY_CODE)).thenThrow(new NotFoundException("Community not found"));

		assertThatThrownBy(() -> service.preview(RAW_TOKEN))
			.isInstanceOf(NotFoundException.class)
			.hasMessage(CommunityJoinInvitationServiceImpl.INVALID_INVITATION_MESSAGE);
	}

	@Test
	void previewLooksTheInvitationUpByHashNeverByRawToken() {
		givenStoredInvitation(storedInvitation(CommunityJoinInvitationStatus.ACTIVE, NOW.plusDays(1), 100, 0));
		when(communityService.getByCode(COMMUNITY_CODE)).thenReturn(communityResponse());

		service.preview(RAW_TOKEN);

		verify(invitationRepository).findByTokenHash(tokenHasher.hash(RAW_TOKEN));
		verify(invitationRepository, never()).findByTokenHash(RAW_TOKEN);
	}

	// ---------- registration ----------

	@Test
	void resolveForRegistrationReusesTheManualCommunityCodeValidation() {
		givenStoredInvitation(storedInvitation(CommunityJoinInvitationStatus.ACTIVE, NOW.plusDays(1), 100, 0));
		when(communityService.getByCode(COMMUNITY_CODE)).thenReturn(communityResponse());

		CommunityJoinInvitationResolution resolution = service.resolveForRegistration(RAW_TOKEN);

		assertThat(resolution.invitationId()).isEqualTo(INVITATION_ID);
		assertThat(resolution.community().id()).isEqualTo(COMMUNITY_ID);
		verify(communityService).getByCode(COMMUNITY_CODE);
	}

	@Test
	void resolveForRegistrationRevalidatesInsteadOfTrustingThePreview() {
		givenStoredInvitation(storedInvitation(CommunityJoinInvitationStatus.REVOKED, NOW.plusDays(1), 100, 0));

		assertThatThrownBy(() -> service.resolveForRegistration(RAW_TOKEN))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void consumeUseRecordsExactlyOneUse() {
		when(invitationRepository.consumeOneUse(
			eq(INVITATION_ID), eq(NOW),
			eq(CommunityJoinInvitationStatus.ACTIVE), eq(CommunityJoinInvitationStatus.EXHAUSTED)
		)).thenReturn(1);

		service.consumeUse(INVITATION_ID);

		verify(invitationRepository).consumeOneUse(
			INVITATION_ID, NOW, CommunityJoinInvitationStatus.ACTIVE, CommunityJoinInvitationStatus.EXHAUSTED
		);
	}

	@Test
	void consumeUseFailsWhenTheInvitationWasConcurrentlyExhausted() {
		when(invitationRepository.consumeOneUse(any(), any(), any(), any())).thenReturn(0);

		assertThatThrownBy(() -> service.consumeUse(INVITATION_ID))
			.isInstanceOf(ConflictException.class)
			.hasMessage(CommunityJoinInvitationServiceImpl.INVALID_INVITATION_MESSAGE);
	}

	// ---------- list and revoke ----------

	@Test
	void listReturnsMetadataOnly() {
		givenManageableCommunity();
		Pageable pageable = PageRequest.of(0, 20);
		Page<CommunityJoinInvitation> page = new PageImpl<>(
			List.of(storedInvitation(CommunityJoinInvitationStatus.ACTIVE, NOW.plusDays(1), 100, 4)), pageable, 1
		);
		when(invitationRepository.findByCommunityIdOrderByCreatedAtDescIdDesc(COMMUNITY_ID, pageable)).thenReturn(page);

		CommunityJoinInvitationResponse item = service.list(COMMUNITY_ID, pageable).getContent().getFirst();

		assertThat(item.invitationId()).isEqualTo(INVITATION_ID);
		assertThat(item.usedCount()).isEqualTo(4);
		assertThat(item.toString()).doesNotContain(RAW_TOKEN).doesNotContain(tokenHasher.hash(RAW_TOKEN));
	}

	@Test
	void listReportsTheEffectiveStatusOfAStoredActiveButExpiredInvitation() {
		givenManageableCommunity();
		Pageable pageable = PageRequest.of(0, 20);
		Page<CommunityJoinInvitation> page = new PageImpl<>(
			List.of(storedInvitation(CommunityJoinInvitationStatus.ACTIVE, NOW.minusHours(1), 100, 0)), pageable, 1
		);
		when(invitationRepository.findByCommunityIdOrderByCreatedAtDescIdDesc(COMMUNITY_ID, pageable)).thenReturn(page);

		assertThat(service.list(COMMUNITY_ID, pageable).getContent().getFirst().status())
			.isEqualTo(CommunityJoinInvitationStatus.EXPIRED);
	}

	@Test
	void revokeMarksTheInvitationRevokedAndStampsTheTime() {
		givenManageableCommunity();
		CommunityJoinInvitation invitation = storedInvitation(CommunityJoinInvitationStatus.ACTIVE, NOW.plusDays(1), 100, 0);
		when(invitationRepository.findByIdAndCommunityId(INVITATION_ID, COMMUNITY_ID)).thenReturn(Optional.of(invitation));

		CommunityJoinInvitationResponse response = service.revoke(COMMUNITY_ID, INVITATION_ID);

		assertThat(invitation.getStatus()).isEqualTo(CommunityJoinInvitationStatus.REVOKED);
		assertThat(invitation.getRevokedAt()).isEqualTo(NOW);
		assertThat(response.status()).isEqualTo(CommunityJoinInvitationStatus.REVOKED);
		verify(invitationRepository).saveAndFlush(invitation);
	}

	@Test
	void revokeIsIdempotent() {
		givenManageableCommunity();
		CommunityJoinInvitation invitation = storedInvitation(CommunityJoinInvitationStatus.REVOKED, NOW.plusDays(1), 100, 0);
		invitation.setRevokedAt(NOW.minusDays(1));
		when(invitationRepository.findByIdAndCommunityId(INVITATION_ID, COMMUNITY_ID)).thenReturn(Optional.of(invitation));

		service.revoke(COMMUNITY_ID, INVITATION_ID);

		assertThat(invitation.getRevokedAt()).isEqualTo(NOW.minusDays(1));
		verify(invitationRepository, never()).saveAndFlush(any());
	}

	@Test
	void revokeCannotReachAnotherCommunitysInvitation() {
		givenManageableCommunity();
		when(invitationRepository.findByIdAndCommunityId(INVITATION_ID, COMMUNITY_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.revoke(COMMUNITY_ID, INVITATION_ID))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void administrativeOperationsAreAlwaysCommunityScoped() {
		doThrowOnScopeCheck();

		assertThatThrownBy(() -> service.list(OTHER_COMMUNITY_ID, PageRequest.of(0, 20)))
			.isInstanceOf(ForbiddenException.class);
		assertThatThrownBy(() -> service.revoke(OTHER_COMMUNITY_ID, INVITATION_ID))
			.isInstanceOf(ForbiddenException.class);

		verifyNoInteractions(invitationRepository);
	}

	@Test
	void administrativeOperationsRequireACommunityId() {
		assertThatThrownBy(() -> service.create(null, CreateCommunityJoinInvitationRequest.builder().build()))
			.isInstanceOf(ValidationException.class);
	}

	private void doThrowOnScopeCheck() {
		org.mockito.Mockito.doThrow(new ForbiddenException("Access denied"))
			.when(communityScopeGuard).assertAccess(any(UUID.class));
	}

	private CommunityJoinInvitation captureSaved() {
		ArgumentCaptor<CommunityJoinInvitation> captor = ArgumentCaptor.forClass(CommunityJoinInvitation.class);
		verify(invitationRepository).saveAndFlush(captor.capture());
		return captor.getValue();
	}
}
