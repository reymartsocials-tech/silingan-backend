package com.ria.olita.tech.silingan.service.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.ria.olita.tech.silingan.config.OnboardingProperties;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinQrImage;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinQrMetadataResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.exception.ConflictException;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.security.scope.CommunityScopeGuard;

class CommunityJoinQrServiceImplTest {

	private static final UUID COMMUNITY_ID = UUID.fromString("5ee38760-cefd-4f0a-a8d2-4941c77fc792");

	private CommunityRepository communityRepository;
	private CommunityScopeGuard communityScopeGuard;
	private CommunityJoinQrServiceImpl service;

	private static Community community(String code, CommunityStatus status) {
		return Community.builder()
			.id(COMMUNITY_ID)
			.name("Maple Residences")
			.communityCode(code)
			.status(status)
			.build();
	}

	private static String decode(byte[] png) throws Exception {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		return new MultiFormatReader()
			.decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))))
			.getText();
	}

	@BeforeEach
	void setUp() {
		communityRepository = mock(CommunityRepository.class);
		communityScopeGuard = mock(CommunityScopeGuard.class);

		OnboardingProperties properties = new OnboardingProperties();
		properties.setJoinBaseUrl("https://app.silingan.ph/join");

		service = new CommunityJoinQrServiceImpl(
			communityRepository,
			communityScopeGuard,
			new CommunityJoinUrlFactory(properties),
			new QrCodeGeneratorImpl()
		);
	}

	@Test
	void generatesQrEncodingTheConfiguredJoinUrlForTheCurrentCommunityCode() throws Exception {
		when(communityRepository.findById(COMMUNITY_ID))
			.thenReturn(Optional.of(community("MAPLERES-2026", CommunityStatus.ACTIVE)));

		CommunityJoinQrImage qr = service.generateJoinQr(COMMUNITY_ID);

		assertThat(qr.content()).isNotEmpty();
		assertThat(qr.fileName()).isEqualTo("silingan-maple-residences-join.png");
		assertThat(decode(qr.content()))
			.isEqualTo("https://app.silingan.ph/join?communityCode=MAPLERES-2026");
	}

	@Test
	void communityScopeIsAssertedBeforeTheCommunityIsRead() {
		doThrow(new ForbiddenException("You do not have access to this community"))
			.when(communityScopeGuard)
			.assertAccess(any(UUID.class));

		assertThatThrownBy(() -> service.generateJoinQr(COMMUNITY_ID))
			.isInstanceOf(ForbiddenException.class);

		verify(communityRepository, never()).findById(any(UUID.class));
	}

	@Test
	void missingCommunityIsReportedAsNotFound() {
		when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.generateJoinQr(COMMUNITY_ID))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void communityNotAcceptingResidentsIsRejected() {
		when(communityRepository.findById(COMMUNITY_ID))
			.thenReturn(Optional.of(community("MAPLERES-2026", CommunityStatus.DRAFT)));
		assertThatThrownBy(() -> service.generateJoinQr(COMMUNITY_ID)).isInstanceOf(ConflictException.class);

		when(communityRepository.findById(COMMUNITY_ID))
			.thenReturn(Optional.of(community("MAPLERES-2026", CommunityStatus.ARCHIVE)));
		assertThatThrownBy(() -> service.generateJoinQr(COMMUNITY_ID)).isInstanceOf(ConflictException.class);
	}

	@Test
	void missingCommunityCodeIsHandledSafely() {
		when(communityRepository.findById(COMMUNITY_ID))
			.thenReturn(Optional.of(community(" ", CommunityStatus.ACTIVE)));

		assertThatThrownBy(() -> service.generateJoinQr(COMMUNITY_ID)).isInstanceOf(ConflictException.class);
	}

	@Test
	void qrIsRenderedFromTheStoredCodeOnEveryRequestSoARotatedCodeTakesEffect() throws Exception {
		when(communityRepository.findById(COMMUNITY_ID))
			.thenReturn(Optional.of(community("MAPLERES-2026", CommunityStatus.ACTIVE)));
		String before = decode(service.generateJoinQr(COMMUNITY_ID).content());

		when(communityRepository.findById(COMMUNITY_ID))
			.thenReturn(Optional.of(community("MAPLERES-2027", CommunityStatus.ACTIVE)));
		String after = decode(service.generateJoinQr(COMMUNITY_ID).content());

		assertThat(before).isEqualTo("https://app.silingan.ph/join?communityCode=MAPLERES-2026");
		assertThat(after).isEqualTo("https://app.silingan.ph/join?communityCode=MAPLERES-2027");
		assertThat(after).isNotEqualTo(before);
	}

	@Test
	void metadataExposesTheJoinUrlAndQrEndpoint() {
		when(communityRepository.findById(COMMUNITY_ID))
			.thenReturn(Optional.of(community("MAPLERES-2026", CommunityStatus.ACTIVE)));

		CommunityJoinQrMetadataResponse metadata = service.getJoinQrMetadata(COMMUNITY_ID);

		assertThat(metadata.communityId()).isEqualTo(COMMUNITY_ID);
		assertThat(metadata.communityName()).isEqualTo("Maple Residences");
		assertThat(metadata.communityCode()).isEqualTo("MAPLERES-2026");
		assertThat(metadata.joinUrl()).isEqualTo("https://app.silingan.ph/join?communityCode=MAPLERES-2026");
		assertThat(metadata.qrCodeUrl())
			.isEqualTo("/api/v1/communities/5ee38760-cefd-4f0a-a8d2-4941c77fc792/join-qr");
	}

	@Test
	void metadataIsAlsoCommunityScoped() {
		doThrow(new ForbiddenException("You do not have access to this community"))
			.when(communityScopeGuard)
			.assertAccess(any(UUID.class));

		assertThatThrownBy(() -> service.getJoinQrMetadata(COMMUNITY_ID))
			.isInstanceOf(ForbiddenException.class);
	}
}
