package com.ria.olita.tech.silingan.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ria.olita.tech.silingan.dto.req.CreateCommunityJoinInvitationRequest;
import com.ria.olita.tech.silingan.dto.res.ApiResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinInvitationCreatedResponse;
import com.ria.olita.tech.silingan.entity.rbac.Action;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.security.permission.RequiresPermission;
import com.ria.olita.tech.silingan.service.invitation.CommunityJoinInvitationService;

class CommunityJoinInvitationControllerTest {

	private static final UUID COMMUNITY_ID = UUID.randomUUID();
	private static final UUID INVITATION_ID = UUID.randomUUID();

	private final CommunityJoinInvitationService service = mock(CommunityJoinInvitationService.class);
	private final CommunityJoinInvitationController controller = new CommunityJoinInvitationController(service);

	@Test
	void requiresAuthentication() {
		PreAuthorize preAuthorize = CommunityJoinInvitationController.class.getAnnotation(PreAuthorize.class);

		assertThat(preAuthorize).isNotNull();
		assertThat(preAuthorize.value()).contains("isAuthenticated()");
	}

	@Test
	void isMountedUnderTheCommunityScopedAdminPath() {
		RequestMapping mapping = CommunityJoinInvitationController.class.getAnnotation(RequestMapping.class);

		assertThat(mapping.value()).containsExactly("/api/v1/communities/{communityId}/join-invitations");
	}

	@Test
	void everyOperationRequiresResidentOnboardingManagement() throws Exception {
		// Distributing join credentials must never be possible with a read-only permission.
		for (String methodName : new String[]{"createInvitation", "listInvitations", "revokeInvitation"}) {
			Method method = findMethod(methodName);
			RequiresPermission permission = method.getAnnotation(RequiresPermission.class);

			assertThat(permission).as(methodName).isNotNull();
			assertThat(permission.domain()).isEqualTo(Domain.RESIDENT);
			assertThat(permission.action()).isEqualTo(Action.MANAGE);
		}
	}

	@Test
	void usesAUuidPathVariableSoTheCommunityScopeAspectApplies() throws Exception {
		Method method = findMethod("createInvitation");

		assertThat(method.getParameterTypes()[0]).isEqualTo(UUID.class);
		assertThat(method.getParameters()[0].getName()).isNotNull();
	}

	@Test
	void createReturnsCreatedWithTheOneTimeJoinMaterial() {
		CommunityJoinInvitationCreatedResponse created = CommunityJoinInvitationCreatedResponse.builder()
			.joinUrl("https://app.silingan.ph/join?token=raw")
			.build();
		when(service.create(eq(COMMUNITY_ID), any())).thenReturn(created);

		ResponseEntity<ApiResponse<CommunityJoinInvitationCreatedResponse>> response =
			controller.createInvitation(COMMUNITY_ID, CreateCommunityJoinInvitationRequest.builder().build());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(response.getBody().data()).isEqualTo(created);
	}

	@Test
	void createAcceptsAnOmittedBodyAndFallsBackToDefaults() {
		controller.createInvitation(COMMUNITY_ID, null);

		verify(service).create(eq(COMMUNITY_ID), any(CreateCommunityJoinInvitationRequest.class));
	}

	@Test
	void revokeDelegatesWithBothPathIdentifiers() {
		controller.revokeInvitation(COMMUNITY_ID, INVITATION_ID);

		verify(service).revoke(COMMUNITY_ID, INVITATION_ID);
	}

	@Test
	void listDelegatesWithPagination() {
		Pageable pageable = PageRequest.of(0, 20);

		controller.listInvitations(COMMUNITY_ID, pageable);

		verify(service).list(COMMUNITY_ID, pageable);
	}

	private Method findMethod(String name) {
		for (Method method : CommunityJoinInvitationController.class.getDeclaredMethods()) {
			if (method.getName().equals(name)) {
				return method;
			}
		}
		throw new AssertionError("Method not found: " + name);
	}
}
