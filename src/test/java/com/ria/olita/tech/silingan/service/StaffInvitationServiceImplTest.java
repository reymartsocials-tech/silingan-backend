package com.ria.olita.tech.silingan.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.ria.olita.tech.silingan.dto.req.CreateStaffInvitationRequest;
import com.ria.olita.tech.silingan.dto.res.StaffInvitationResponse;
import com.ria.olita.tech.silingan.dto.res.StaffInvitationSummaryResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.StaffInvitation;
import com.ria.olita.tech.silingan.entity.StaffInvitationStatus;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.StaffInvitationRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityStaffRoleRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.service.KeycloakService;
import com.ria.olita.tech.silingan.service.impl.StaffInvitationServiceImpl;
import com.ria.olita.tech.silingan.service.sms.SmsService;
import com.ria.olita.tech.silingan.util.TokenGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffInvitationServiceImplTest {

	private final StaffInvitationRepository invitationRepository = Mockito.mock(StaffInvitationRepository.class);
	private final CommunityRepository communityRepository = Mockito.mock(CommunityRepository.class);
	private final UserRepository userRepository = Mockito.mock(UserRepository.class);
	private final UserCommunityRepository userCommunityRepository = Mockito.mock(UserCommunityRepository.class);
	private final UserCommunityStaffRoleRepository staffRoleRepository = Mockito.mock(UserCommunityStaffRoleRepository.class);
	private final KeycloakService keycloakService = Mockito.mock(KeycloakService.class);
	private final SmsService smsService = Mockito.mock(SmsService.class);
	private final TokenGenerator tokenGenerator = Mockito.mock(TokenGenerator.class);

	private final StaffInvitationServiceImpl service = new StaffInvitationServiceImpl(
		invitationRepository,
		communityRepository,
		userRepository,
		userCommunityRepository,
		staffRoleRepository,
		keycloakService,
		smsService,
		tokenGenerator
	);

	@AfterEach
	void tearDown() {
		UserContextHolder.clear();
	}

	@Test
	void createInvitationSuccessfully() {
		UUID communityId = UUID.randomUUID();
		UUID inviterId = UUID.randomUUID();
		Community community = Community.builder().id(communityId).build();
		User inviter = User.builder().id(inviterId).build();

		CreateStaffInvitationRequest request = new CreateStaffInvitationRequest(
			"John", "Doe", "john@example.com", "+639123456789", "Manager",
			StaffRoleCode.PMO_STAFF, "Notes"
		);

		UserContextHolder.set(UserContext.builder()
			.userId(inviterId.toString())
			.communityId(communityId.toString())
			.build());

		Mockito.when(communityRepository.findByIdAndStatus(communityId, CommunityStatus.ACTIVE)).thenReturn(Optional.of(community));
		Mockito.when(userRepository.findById(inviterId)).thenReturn(Optional.of(inviter));
		Mockito.when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
		Mockito.when(invitationRepository.findActivePendingByEmailAndCommunity(communityId, request.email()))
			.thenReturn(Optional.empty());
		Mockito.when(tokenGenerator.generateSecureToken()).thenReturn("secure-token-123");

		StaffInvitation savedInvitation = StaffInvitation.builder()
			.id(UUID.randomUUID())
			.community(community)
			.firstName(request.firstName())
			.lastName(request.lastName())
			.email(request.email())
			.mobileNumber(request.mobileNumber())
			.position(request.position())
			.roleCode(request.roleCode())
			.token("secure-token-123")
			.status(StaffInvitationStatus.PENDING)
			.invitedAt(LocalDateTime.now(ZoneId.of("UTC")))
			.expiresAt(LocalDateTime.now(ZoneId.of("UTC")).plusDays(7))
			.invitedBy(inviter)
			.build();

		Mockito.when(invitationRepository.save(Mockito.any(StaffInvitation.class))).thenReturn(savedInvitation);

		StaffInvitationResponse response = service.createInvitation(communityId, request);

		assertThat(response).isNotNull();
		assertThat(response.email()).isEqualTo(request.email());
		assertThat(response.firstName()).isEqualTo(request.firstName());
		assertThat(response.roleCode()).isEqualTo(request.roleCode());
		assertThat(response.status()).isEqualTo(StaffInvitationStatus.PENDING);
	}

	@Test
	void createInvitationFailsForDuplicatePending() {
		UUID communityId = UUID.randomUUID();
		UUID inviterId = UUID.randomUUID();
		Community community = Community.builder().id(communityId).build();

		CreateStaffInvitationRequest request = new CreateStaffInvitationRequest(
			"John", "Doe", "john@example.com", "+639123456789", "Manager",
			StaffRoleCode.PMO_STAFF, null
		);

		UserContextHolder.set(UserContext.builder()
			.userId(inviterId.toString())
			.communityId(communityId.toString())
			.build());

		Mockito.when(communityRepository.findByIdAndStatus(communityId, CommunityStatus.ACTIVE)).thenReturn(Optional.of(community));
		Mockito.when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
		
		StaffInvitation existingInvitation = StaffInvitation.builder()
			.id(UUID.randomUUID())
			.status(StaffInvitationStatus.PENDING)
			.build();
		Mockito.when(invitationRepository.findActivePendingByEmailAndCommunity(communityId, request.email()))
			.thenReturn(Optional.of(existingInvitation));

		assertThatThrownBy(() -> service.createInvitation(communityId, request))
			.isInstanceOf(ForbiddenException.class)
			.hasMessageContaining("pending invitation already exists");
	}

	@Test
	void getSummaryReturnsCorrectCounts() {
		UUID communityId = UUID.randomUUID();

		Mockito.when(communityRepository.existsById(communityId)).thenReturn(true);
		Mockito.when(invitationRepository.countByCommunityId(communityId)).thenReturn(10L);
		Mockito.when(invitationRepository.countByCommunityIdAndStatus(communityId, StaffInvitationStatus.PENDING)).thenReturn(3L);
		Mockito.when(invitationRepository.countByCommunityIdAndStatus(communityId, StaffInvitationStatus.ACCEPTED)).thenReturn(5L);
		Mockito.when(invitationRepository.countByCommunityIdAndStatus(communityId, StaffInvitationStatus.EXPIRED)).thenReturn(1L);
		Mockito.when(invitationRepository.countByCommunityIdAndStatus(communityId, StaffInvitationStatus.REVOKED)).thenReturn(1L);

		StaffInvitationSummaryResponse summary = service.getSummary(communityId);

		assertThat(summary.total()).isEqualTo(10L);
		assertThat(summary.pending()).isEqualTo(3L);
		assertThat(summary.accepted()).isEqualTo(5L);
		assertThat(summary.expired()).isEqualTo(1L);
		assertThat(summary.revoked()).isEqualTo(1L);
	}

	@Test
	void expireOldInvitationsMarksExpiredAsExpired() {
		LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
		
		Mockito.when(invitationRepository.expireOldPending(Mockito.any(LocalDateTime.class)))
			.thenReturn(5);

		int count = service.expireOldInvitations();

		assertThat(count).isEqualTo(5);
		Mockito.verify(invitationRepository).expireOldPending(Mockito.any(LocalDateTime.class));
	}

	@Test
	void revokeInvitationSuccessfully() {
		UUID communityId = UUID.randomUUID();
		UUID invitationId = UUID.randomUUID();

		StaffInvitation invitation = StaffInvitation.builder()
			.id(invitationId)
			.communityId(communityId)
			.status(StaffInvitationStatus.PENDING)
			.build();

		Mockito.when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
		Mockito.when(invitationRepository.save(Mockito.any(StaffInvitation.class))).thenReturn(invitation);

		StaffInvitationResponse response = service.revokeInvitation(communityId, invitationId, "No longer needed");

		assertThat(response.status()).isEqualTo(StaffInvitationStatus.REVOKED);
		Mockito.verify(invitationRepository).save(Mockito.any(StaffInvitation.class));
	}

	@Test
	void getByTokenSuccessfully() {
		String token = "test-token";
		StaffInvitation invitation = StaffInvitation.builder()
			.id(UUID.randomUUID())
			.token(token)
			.build();

		Mockito.when(invitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));

		StaffInvitation result = service.getByToken(token);

		assertThat(result).isNotNull();
		assertThat(result.getToken()).isEqualTo(token);
	}

	@Test
	void getByTokenThrowsNotFoundForInvalidToken() {
		String token = "invalid-token";
		Mockito.when(invitationRepository.findByToken(token)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getByToken(token))
			.isInstanceOf(NotFoundException.class);
	}
}
