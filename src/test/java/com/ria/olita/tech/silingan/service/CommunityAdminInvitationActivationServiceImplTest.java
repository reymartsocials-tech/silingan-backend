package com.ria.olita.tech.silingan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityAdminInvitation;
import com.ria.olita.tech.silingan.entity.CommunityAdminInvitationStatus;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.repository.CommunityAdminInvitationRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.service.impl.CommunityAdminInvitationActivationServiceImpl;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class CommunityAdminInvitationActivationServiceImplTest {

	@Test
	void shouldActivatePendingInvitationWhenRequiredActionsCompleted() {
		CommunityAdminInvitationRepository invitationRepository = Mockito.mock(CommunityAdminInvitationRepository.class);
		UserRepository userRepository = Mockito.mock(UserRepository.class);
		UserCommunityRepository userCommunityRepository = Mockito.mock(UserCommunityRepository.class);
		KeycloakService keycloakService = Mockito.mock(KeycloakService.class);
		CommunityAdminInvitationActivationServiceImpl service = new CommunityAdminInvitationActivationServiceImpl(
			invitationRepository,
			userRepository,
			userCommunityRepository,
			keycloakService
		);

		UUID communityId = UUID.randomUUID();
		Community community = Community.builder().id(communityId).build();
		CommunityAdminInvitation invitation = CommunityAdminInvitation.builder()
			.community(community)
			.email("john.doe@company.com")
			.keycloakUserId("kc-1")
			.status(CommunityAdminInvitationStatus.PENDING)
			.build();

		User user = User.builder().id(UUID.randomUUID()).keycloakUserId("kc-1").email("john.doe@company.com").build();

		when(invitationRepository.findByKeycloakUserIdAndStatusAndCommunityId("kc-1", CommunityAdminInvitationStatus.PENDING,community.getId()))
			.thenReturn(List.of(invitation));
		when(keycloakService.isInvitationCompleted(eq("kc-1"), any()))
			.thenReturn(true);
		when(userRepository.findByKeycloakUserId("kc-1"))
			.thenReturn(Optional.of(user));
		when(userCommunityRepository.hasRoleInCommunity(communityId, SilinganRealmRole.COMMUNITY_ADMIN))
			.thenReturn(false);
		when(userCommunityRepository.findByUserIdAndCommunityId(user.getId(), communityId))
			.thenReturn(Optional.empty());

		service.activateIfCompleted("kc-1",communityId);

		verify(keycloakService).assignRealmRole("kc-1", SilinganRealmRole.COMMUNITY_ADMIN.name());
		verify(userCommunityRepository).save(any(UserCommunity.class));
		assertThat(invitation.getStatus()).isEqualTo(CommunityAdminInvitationStatus.ACCEPTED);
		assertThat(invitation.getAcceptedAt()).isNotNull();
	}

	@Test
	void shouldNotActivateWhenRequiredActionsAreStillPending() {
		CommunityAdminInvitationRepository invitationRepository = Mockito.mock(CommunityAdminInvitationRepository.class);
		UserRepository userRepository = Mockito.mock(UserRepository.class);
		UserCommunityRepository userCommunityRepository = Mockito.mock(UserCommunityRepository.class);
		KeycloakService keycloakService = Mockito.mock(KeycloakService.class);
		CommunityAdminInvitationActivationServiceImpl service = new CommunityAdminInvitationActivationServiceImpl(
			invitationRepository,
			userRepository,
			userCommunityRepository,
			keycloakService
		);

		Community community = Community.builder().id(UUID.randomUUID()).build();
		CommunityAdminInvitation invitation = CommunityAdminInvitation.builder()
			.community(community)
			.email("john.doe@company.com")
			.keycloakUserId("kc-1")
			.status(CommunityAdminInvitationStatus.PENDING)
			.build();

		when(invitationRepository.findByKeycloakUserIdAndStatusAndCommunityId("kc-1", CommunityAdminInvitationStatus.PENDING, community.getId()))
			.thenReturn(List.of(invitation));
		when(keycloakService.isInvitationCompleted(eq("kc-1"), any()))
			.thenReturn(false);

		service.activateIfCompleted("kc-1", community.getId());

		verify(userCommunityRepository, never()).save(any(UserCommunity.class));
		verify(userRepository, never()).save(any(User.class));
		assertThat(invitation.getStatus()).isEqualTo(CommunityAdminInvitationStatus.PENDING);
	}
}

