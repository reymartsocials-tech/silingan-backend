package com.ria.olita.tech.silingan.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import com.ria.olita.tech.silingan.dto.res.ApiResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinQrImage;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinQrMetadataResponse;
import com.ria.olita.tech.silingan.entity.rbac.Action;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.security.permission.RequiresPermission;
import com.ria.olita.tech.silingan.security.scope.SkipCommunityScopeCheck;
import com.ria.olita.tech.silingan.service.qr.CommunityJoinQrService;

class CommunityJoinQrControllerTest {

	private static final UUID COMMUNITY_ID = UUID.fromString("5ee38760-cefd-4f0a-a8d2-4941c77fc792");

	private final CommunityJoinQrService service = mock(CommunityJoinQrService.class);
	private final CommunityJoinQrController controller = new CommunityJoinQrController(service);

	@Test
	void requiresAnAuthenticatedCallerWithCommunityViewPermission() throws Exception {
		PreAuthorize pre = CommunityJoinQrController.class.getAnnotation(PreAuthorize.class);
		assertThat(pre).isNotNull();
		assertThat(pre.value()).isEqualTo("isAuthenticated()");

		Method method = CommunityJoinQrController.class.getMethod("getJoinQr", UUID.class);
		RequiresPermission permission = method.getAnnotation(RequiresPermission.class);
		assertThat(permission).isNotNull();
		assertThat(permission.domain()).isEqualTo(Domain.COMMUNITY);
		assertThat(permission.action()).isEqualTo(Action.VIEW);
	}

	@Test
	void communityScopeCheckIsNotSkipped() throws Exception {
		// The community scope aspect is what stops an admin of community A reading community B.
		assertThat(CommunityJoinQrController.class.getAnnotation(SkipCommunityScopeCheck.class)).isNull();
		assertThat(CommunityJoinQrController.class.getMethod("getJoinQr", UUID.class)
			.getAnnotation(SkipCommunityScopeCheck.class)).isNull();
	}

	@Test
	void returnsPngWithPrivateCacheAndInlineDisposition() {
		when(service.generateJoinQr(COMMUNITY_ID)).thenReturn(CommunityJoinQrImage.builder()
			.fileName("silingan-maple-residences-join.png")
			.content(new byte[]{1, 2, 3})
			.build());

		ResponseEntity<byte[]> response = controller.getJoinQr(COMMUNITY_ID);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getHeaders()
			.getContentType()).isEqualTo(MediaType.IMAGE_PNG);
		assertThat(response.getHeaders()
			.getCacheControl()).isEqualTo("max-age=300, private");
		assertThat(response.getHeaders()
			.getFirst(HttpHeaders.CONTENT_DISPOSITION))
			.isEqualTo("inline; filename=\"silingan-maple-residences-join.png\"");
		assertThat(response.getBody()).isNotEmpty();
	}

	@Test
	void forbiddenAndNotFoundPropagateToTheStandardErrorHandling() {
		when(service.generateJoinQr(COMMUNITY_ID)).thenThrow(new ForbiddenException("You do not have access to this community"));
		assertThatThrownBy(() -> controller.getJoinQr(COMMUNITY_ID)).isInstanceOf(ForbiddenException.class);

		when(service.getJoinQrMetadata(COMMUNITY_ID)).thenThrow(new NotFoundException("Community not found"));
		assertThatThrownBy(() -> controller.getJoinQrMetadata(COMMUNITY_ID)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void metadataIsReturnedInTheStandardEnvelope() {
		when(service.getJoinQrMetadata(COMMUNITY_ID)).thenReturn(CommunityJoinQrMetadataResponse.builder()
			.communityId(COMMUNITY_ID)
			.communityName("Maple Residences")
			.communityCode("MAPLERES-2026")
			.joinUrl("https://app.silingan.ph/join?communityCode=MAPLERES-2026")
			.qrCodeUrl("/api/v1/communities/" + COMMUNITY_ID + "/join-qr")
			.build());

		ResponseEntity<ApiResponse<CommunityJoinQrMetadataResponse>> response =
			controller.getJoinQrMetadata(COMMUNITY_ID);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody()
			.success()).isTrue();
		assertThat(response.getBody()
			.data()
			.joinUrl()).isEqualTo("https://app.silingan.ph/join?communityCode=MAPLERES-2026");
	}
}
