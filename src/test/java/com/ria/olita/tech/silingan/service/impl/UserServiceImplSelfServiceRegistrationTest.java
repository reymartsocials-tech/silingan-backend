package com.ria.olita.tech.silingan.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ria.olita.tech.silingan.dto.req.CreateUserRequest;
import com.ria.olita.tech.silingan.dto.res.CommunityResponse;
import com.ria.olita.tech.silingan.dto.res.CreatedUserResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.exception.ConflictException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.service.CommunityService;
import com.ria.olita.tech.silingan.service.KeycloakService;
import com.ria.olita.tech.silingan.service.invitation.CommunityJoinInvitationResolution;
import com.ria.olita.tech.silingan.service.invitation.CommunityJoinInvitationService;
import com.ria.olita.tech.silingan.service.otp.RegistrationOtpProofService;

/**
 * Both onboarding entry points must reach the same registration path; only the way the community is
 * selected differs.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplSelfServiceRegistrationTest {

	private static final UUID COMMUNITY_ID = UUID.fromString("5ee38760-cefd-4f0a-a8d2-4941c77fc792");
	private static final UUID INVITATION_ID = UUID.fromString("c5931ff1-9bce-41a4-93ff-508a674b8611");
	private static final String COMMUNITY_CODE = "MAPLERES-2026";
	private static final String RAW_TOKEN = "Y8hVfM3wQk7xP2nR6cT9aB1cD2eF3gH4iJ5kL6mN7oP";
	private static final String MOBILE_NUMBER = "+639171234567";

	@Mock
	private KeycloakService keycloakService;
	@Mock
	private UserRepository userRepository;
	@Mock
	private CommunityRepository communityRepository;
	@Mock
	private RegistrationOtpProofService registrationOtpProofService;
	@Mock
	private CommunityService communityService;
	@Mock
	private CommunityJoinInvitationService communityJoinInvitationService;

	@InjectMocks
	private UserServiceImpl userService;

	@BeforeEach
	void setUp() {
		Community community = new Community();
		community.setId(COMMUNITY_ID);
		community.setName("Maple Residences");
		community.setCommunityCode(COMMUNITY_CODE);
		community.setStatus(CommunityStatus.ACTIVE);

		when(registrationOtpProofService.consumeRegistrationProof(anyString())).thenReturn(true);
		when(communityRepository.findById(COMMUNITY_ID)).thenReturn(Optional.of(community));
		when(keycloakService.createUser(any(), any())).thenReturn("kc-user-id");
		when(communityService.getByCode(COMMUNITY_CODE)).thenReturn(communityResponse());
		when(communityJoinInvitationService.resolveForRegistration(RAW_TOKEN))
			.thenReturn(new CommunityJoinInvitationResolution(INVITATION_ID, communityResponse()));
	}

	private static CommunityResponse communityResponse() {
		return CommunityResponse.builder()
			.id(COMMUNITY_ID)
			.communityCode(COMMUNITY_CODE)
			.name("Maple Residences")
			.status(CommunityStatus.ACTIVE)
			.build();
	}

	private static CreateUserRequest.CreateUserRequestBuilder resident() {
		return CreateUserRequest.builder()
			.firstName("Juan")
			.lastName("Dela Cruz")
			.mobileNumber(MOBILE_NUMBER);
	}

	@Test
	void manualCommunityCodeOnboardingIsUnchanged() {
		CreatedUserResponse response =
			userService.createSelfServiceUser(resident().communityCode(COMMUNITY_CODE).build());

		assertThat(response.communityId()).isEqualTo(COMMUNITY_ID);
		verify(communityService).getByCode(COMMUNITY_CODE);
		verify(communityJoinInvitationService, never()).resolveForRegistration(anyString());
		verify(communityJoinInvitationService, never()).consumeUse(any());
	}

	@Test
	void invitationTokenOnboardingResolvesTheCommunityOnTheServer() {
		CreatedUserResponse response =
			userService.createSelfServiceUser(resident().invitationToken(RAW_TOKEN).build());

		assertThat(response.communityId()).isEqualTo(COMMUNITY_ID);
		verify(communityJoinInvitationService).resolveForRegistration(RAW_TOKEN);
		verify(communityService, never()).getByCode(anyString());
	}

	@Test
	void invitationUsageIsRecordedOnlyAfterTheMembershipIsPersisted() {
		org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(userRepository, communityJoinInvitationService);

		userService.createSelfServiceUser(resident().invitationToken(RAW_TOKEN).build());

		inOrder.verify(userRepository).saveAndFlush(any());
		inOrder.verify(communityJoinInvitationService).consumeUse(INVITATION_ID);
	}

	@Test
	void stillRequiresOtpVerificationForInvitationOnboarding() {
		when(registrationOtpProofService.consumeRegistrationProof(anyString())).thenReturn(false);

		assertThatThrownBy(() -> userService.createSelfServiceUser(resident().invitationToken(RAW_TOKEN).build()))
			.isInstanceOf(com.ria.olita.tech.silingan.exception.ForbiddenException.class);

		verify(communityJoinInvitationService, never()).resolveForRegistration(anyString());
	}

	@Test
	void aConcurrentlyExhaustedInvitationFailsTheRegistration() {
		org.mockito.Mockito.doThrow(new ConflictException("Invitation no longer available"))
			.when(communityJoinInvitationService).consumeUse(INVITATION_ID);

		assertThatThrownBy(() -> userService.createSelfServiceUser(resident().invitationToken(RAW_TOKEN).build()))
			.isInstanceOf(ConflictException.class);
	}

	@Test
	void rejectsARequestWithNeitherSelectionMechanism() {
		assertThatThrownBy(() -> userService.createSelfServiceUser(resident().build()))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsARequestWithBothSelectionMechanisms() {
		assertThatThrownBy(() -> userService.createSelfServiceUser(
			resident().communityCode(COMMUNITY_CODE).invitationToken(RAW_TOKEN).build()
		)).isInstanceOf(ValidationException.class);
	}
}
