package com.ria.olita.tech.silingan.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import com.ria.olita.tech.silingan.config.OnboardingProperties;
import com.ria.olita.tech.silingan.dto.res.CommunityResponse;
import com.ria.olita.tech.silingan.service.CommunityService;
import com.ria.olita.tech.silingan.service.qr.CommunityJoinUrlFactory;

/**
 * The QR code is only another way of supplying the community code: a code decoded from a join QR
 * must reach exactly the same validation path as a manually typed code.
 */
class PublicCommunityControllerQrParityTest {

	private static final String COMMUNITY_CODE = "MAPLERES-2026";

	private final CommunityService communityService = mock(CommunityService.class);
	private final PublicCommunityController controller = new PublicCommunityController(communityService);

	private static String communityCodeFromJoinUrl(URI joinUrl) {
		return UriComponentsBuilder.fromUri(joinUrl)
			.build()
			.getQueryParams()
			.getFirst(CommunityJoinUrlFactory.COMMUNITY_CODE_PARAM);
	}

	@Test
	void manualEntryAndQrDerivedCodeUseTheSameValidationService() {
		OnboardingProperties properties = new OnboardingProperties();
		properties.setJoinBaseUrl("https://app.silingan.ph/join");
		URI joinUrl = new CommunityJoinUrlFactory(properties).create(COMMUNITY_CODE);

		CommunityResponse resolved = CommunityResponse.builder()
			.id(UUID.randomUUID())
			.communityCode(COMMUNITY_CODE)
			.name("Maple Residences")
			.build();
		when(communityService.getByCode(COMMUNITY_CODE)).thenReturn(resolved);

		// Manual path: the resident types the code.
		var manual = controller.getByCode(COMMUNITY_CODE);

		// QR path: the scanner decodes the join URL and the frontend extracts the same code.
		String scannedCode = communityCodeFromJoinUrl(joinUrl);
		var scanned = controller.getByCode(scannedCode);

		assertThat(scannedCode).isEqualTo(COMMUNITY_CODE);
		verify(communityService, times(2)).getByCode(COMMUNITY_CODE);
		assertThat(manual.getBody()).isNotNull();
		assertThat(scanned.getBody()).isNotNull();
		assertThat(scanned.getBody()
			.data()).isEqualTo(manual.getBody()
			.data());
	}

	@Test
	void resolvingACommunityDoesNotCreateAnyMembership() {
		when(communityService.getByCode(COMMUNITY_CODE)).thenReturn(CommunityResponse.builder()
			.id(UUID.randomUUID())
			.communityCode(COMMUNITY_CODE)
			.name("Maple Residences")
			.build());

		controller.getByCode(COMMUNITY_CODE);

		// Validation is a read-only lookup; onboarding stays with the existing registration flow.
		verify(communityService, times(1)).getByCode(COMMUNITY_CODE);
		org.mockito.Mockito.verifyNoMoreInteractions(communityService);
	}
}
