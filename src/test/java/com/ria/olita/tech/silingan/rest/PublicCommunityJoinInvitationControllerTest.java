package com.ria.olita.tech.silingan.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ria.olita.tech.silingan.dto.req.CommunityJoinInvitationPreviewRequest;
import com.ria.olita.tech.silingan.dto.res.ApiResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinInvitationPreviewResponse;
import com.ria.olita.tech.silingan.service.invitation.CommunityJoinInvitationService;

import jakarta.annotation.security.PermitAll;

class PublicCommunityJoinInvitationControllerTest {

	private final CommunityJoinInvitationService service = mock(CommunityJoinInvitationService.class);
	private final PublicCommunityJoinInvitationController controller =
		new PublicCommunityJoinInvitationController(service);

	@Test
	void isMountedUnderThePublicPathCoveredBySecurityConfig() {
		RequestMapping mapping = PublicCommunityJoinInvitationController.class.getAnnotation(RequestMapping.class);

		assertThat(mapping.value()).containsExactly("/api/v1/public/community-join-invitations");
	}

	@Test
	void previewIsReachableBeforeAuthentication() throws Exception {
		Method method = PublicCommunityJoinInvitationController.class
			.getDeclaredMethod("preview", CommunityJoinInvitationPreviewRequest.class);

		assertThat(method.getAnnotation(PermitAll.class)).isNotNull();
	}

	@Test
	void previewAcceptsTheTokenInTheRequestBodyNotTheQueryString() throws Exception {
		// Keeps the raw token out of browser history, proxy logs and gateway access logs.
		Method method = PublicCommunityJoinInvitationController.class
			.getDeclaredMethod("preview", CommunityJoinInvitationPreviewRequest.class);

		assertThat(method.getAnnotation(PostMapping.class)).isNotNull();
		assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly("/preview");
	}

	@Test
	void previewDelegatesTheRawTokenToTheService() {
		CommunityJoinInvitationPreviewResponse preview = CommunityJoinInvitationPreviewResponse.builder()
			.communityName("Maple Residences")
			.build();
		when(service.preview("raw-token")).thenReturn(preview);

		ResponseEntity<ApiResponse<CommunityJoinInvitationPreviewResponse>> response = controller.preview(
			CommunityJoinInvitationPreviewRequest.builder().token("raw-token").build()
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().data()).isEqualTo(preview);
		verify(service).preview("raw-token");
	}
}
