package com.ria.olita.tech.silingan.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.ria.olita.tech.silingan.dto.req.InvitationRequest;
import com.ria.olita.tech.silingan.dto.res.InvitationSummaryResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.Invitation;
import com.ria.olita.tech.silingan.entity.InvitationStatus;
import com.ria.olita.tech.silingan.entity.InvitationType;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.UserStatus;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.event.InvitationCreatedEvent;
import com.ria.olita.tech.silingan.exception.ConflictException;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.mapper.InvitationMapper;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.InvitationRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.service.KeycloakService;

@DisplayName("InviteServiceImpl Tests")
class InviteServiceImplTest {

	private InviteServiceImpl inviteService;
	private InvitationRepository invitationRepository;
	private CommunityRepository communityRepository;
	private UserRepository userRepository;
	private UserCommunityRepository userCommunityRepository;
	private KeycloakService keycloakService;
	private InvitationMapper invitationMapper;
	private ApplicationEventPublisher eventPublisher;
	private PendingUserAndMembershipCreator pendingUserAndMembershipCreator;

	private UUID communityId;
	private UUID userId;
	private Community community;

	@BeforeEach
	void setUp() {
		invitationRepository = mock(InvitationRepository.class);
		communityRepository = mock(CommunityRepository.class);
		userRepository = mock(UserRepository.class);
		userCommunityRepository = mock(UserCommunityRepository.class);
		keycloakService = mock(KeycloakService.class);
		invitationMapper = mock(InvitationMapper.class);
		eventPublisher = mock(ApplicationEventPublisher.class);
		pendingUserAndMembershipCreator = mock(PendingUserAndMembershipCreator.class);

		inviteService = new InviteServiceImpl(
			invitationRepository,
			communityRepository,
			userRepository,
			userCommunityRepository,
			keycloakService,
			invitationMapper,
			eventPublisher,
			pendingUserAndMembershipCreator
		);

		communityId = UUID.randomUUID();
		userId = UUID.randomUUID();

		community = Community.builder()
			.id(communityId)
			.name("Test Community")
			.status(CommunityStatus.ACTIVE)
			.build();
	}

	@Test
	@DisplayName("Should create staff invitation with pending user and membership")
	void testInviteStaff_CreatesUserAndMembership() {
		// Arrange
		String newUserEmail = "newuser@example.com";
		String keycloakUserId = "keycloak-newuser-123";
		InvitationRequest request = new InvitationRequest(
			newUserEmail,
			"PMO_STAFF",
			"Test notes"
		);

		when(communityRepository.findByIdAndStatus(eq(communityId), eq(CommunityStatus.ACTIVE)))
			.thenReturn(Optional.of(community));
		when(invitationRepository.findActivePendingByEmailAndCommunity(
			eq(communityId), eq(InvitationType.STAFF), eq(newUserEmail),
			eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
			.thenReturn(Optional.empty());
		when(userCommunityRepository.existsActiveMemberByEmailAndCommunityId(eq(communityId), eq(newUserEmail)))
			.thenReturn(false);
		when(invitationRepository.save(any(Invitation.class)))
			.thenAnswer(inv -> {
				Invitation i = (Invitation) inv.getArgument(0);
				if (i.getId() == null) {
					i.setId(UUID.randomUUID());
				}
				return i;
			});
		when(keycloakService.createInvitationUserWithRoles(eq(newUserEmail), anyList()))
			.thenReturn(keycloakUserId);
		when(invitationMapper.toSummaryResponse(any(Invitation.class)))
			.thenReturn(new InvitationSummaryResponse(
				UUID.randomUUID(), newUserEmail, newUserEmail,
				StaffRoleCode.PMO_STAFF, "PENDING", LocalDateTime.now(ZoneOffset.UTC),
				LocalDateTime.now(ZoneOffset.UTC)
					.plusDays(7), null, "Test notes", null
			));

		// Act
		InvitationSummaryResponse response = inviteService.invite(communityId, request);

		// Assert
		assertThat(response).isNotNull();
		assertThat(response.email()).isEqualTo(newUserEmail);
		assertThat(response.status()).isEqualTo("PENDING");

		// Verify Keycloak user was created with STAFF role
		verify(keycloakService).createInvitationUserWithRoles(eq(newUserEmail), eq(List.of(SilinganRealmRole.STAFF.name())));

		// Verify invitation was saved twice (before and after Keycloak creation)
		verify(invitationRepository, times(2)).save(any(Invitation.class));

		// Verify pending user and membership were created
		verify(pendingUserAndMembershipCreator).createPendingUserAndMembership(any(Invitation.class), eq(keycloakUserId));

		// Verify event was published with valid keycloakUserId for email sending
		ArgumentCaptor<InvitationCreatedEvent> eventCaptor = ArgumentCaptor.forClass(InvitationCreatedEvent.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue().getEmail()).isEqualTo(newUserEmail);
		assertThat(eventCaptor.getValue().getInvitationType()).isEqualTo(InvitationType.STAFF);
		assertThat(eventCaptor.getValue().getKeycloakUserId()).isEqualTo(keycloakUserId);
	}

	@Test
	@DisplayName("Should throw ForbiddenException when non-admin invites admin (authorization check before conflict checks)")
	void testInviteAdmin_ForbiddenForNonAdmin() {
		// Arrange
		InvitationRequest request = new InvitationRequest("admin@example.com", "COMMUNITY_ADMIN", "");

		// Authorization check happens BEFORE any other validation
		// So ForbiddenException is thrown before checking if community already has admin
		// This is correct behavior - authorization first

		// Act & Assert
		assertThatThrownBy(() -> inviteService.invite(communityId, request))
			.isInstanceOf(ForbiddenException.class)
			.hasMessageContaining("Only platform admins can invite community admins");
	}

	@Test
	@DisplayName("Should activate user and membership on first login")
	void testActivateInvitation_ActivatesPendingUserAndMembership() {
		// Arrange
		String keycloakUserId = "keycloak-user-123";
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		LocalDateTime expiresAt = now.plusDays(7);

		Invitation invitation = Invitation.builder()
			.id(UUID.randomUUID())
			.type(InvitationType.STAFF)
			.community(community)
			.communityId(communityId)
			.email("user@example.com")
			.keycloakUserId(keycloakUserId)
			.status(InvitationStatus.PENDING)
			.invitedAt(now)
			.expiresAt(expiresAt)
			.build();

		User pendingUser = User.builder()
			.id(userId)
			.keycloakUserId(keycloakUserId)
			.email("user@example.com")
			.status(UserStatus.PENDING)
			.build();

		UserCommunity pendingMembership = UserCommunity.builder()
			.id(UUID.randomUUID())
			.user(pendingUser)
			.community(community)
			.role(SilinganRealmRole.STAFF)
			.build();

		when(invitationRepository.findActivePendingAdminInvitation(eq(keycloakUserId), eq(communityId), eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
			.thenReturn(Optional.empty());
		when(invitationRepository.findPendingByKeycloakUserIdAndType(eq(keycloakUserId), eq(InvitationType.STAFF), eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
			.thenReturn(Optional.of(invitation));
		when(keycloakService.isInvitationCompleted(eq(keycloakUserId), anyList()))
			.thenReturn(true);
		when(userRepository.findByKeycloakUserId(keycloakUserId))
			.thenReturn(Optional.of(pendingUser));
		when(userCommunityRepository.findByUserIdAndCommunityId(userId, communityId))
			.thenReturn(Optional.of(pendingMembership));
		when(userRepository.save(any(User.class)))
			.thenAnswer(inv -> inv.getArgument(0));
		when(userCommunityRepository.save(any(UserCommunity.class)))
			.thenAnswer(inv -> inv.getArgument(0));
		when(invitationRepository.save(any(Invitation.class)))
			.thenAnswer(inv -> inv.getArgument(0));

		// Act
		inviteService.activateInvitation(keycloakUserId, communityId, "user@example.com");

		// Assert
		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		assertThat(userCaptor.getValue()
			.getStatus()).isEqualTo(UserStatus.ACTIVE);

		ArgumentCaptor<UserCommunity> membershipCaptor = ArgumentCaptor.forClass(UserCommunity.class);
		verify(userCommunityRepository).save(membershipCaptor.capture());

		ArgumentCaptor<Invitation> invitationCaptor = ArgumentCaptor.forClass(Invitation.class);
		verify(invitationRepository).save(invitationCaptor.capture());
		assertThat(invitationCaptor.getValue()
			.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
		assertThat(invitationCaptor.getValue()
			.getAcceptedAt()).isNotNull();
	}

	@Test
	@DisplayName("Should skip activation if Keycloak actions not completed")
	void testActivateInvitation_SkipsIfActionsNotCompleted() {
		// Arrange
		String keycloakUserId = "keycloak-user-123";
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

		Invitation invitation = Invitation.builder()
			.id(UUID.randomUUID())
			.type(InvitationType.STAFF)
			.community(community)
			.communityId(communityId)
			.email("user@example.com")
			.keycloakUserId(keycloakUserId)
			.status(InvitationStatus.PENDING)
			.invitedAt(now)
			.expiresAt(now.plusDays(7))
			.build();

		when(invitationRepository.findActivePendingAdminInvitation(eq(keycloakUserId), eq(communityId), eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
			.thenReturn(Optional.empty());
		when(invitationRepository.findPendingByKeycloakUserIdAndType(eq(keycloakUserId), eq(InvitationType.STAFF), eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
			.thenReturn(Optional.of(invitation));
		when(keycloakService.isInvitationCompleted(eq(keycloakUserId), anyList()))
			.thenReturn(false);

		// Act
		inviteService.activateInvitation(keycloakUserId, communityId, "user@example.com");

		// Assert
		verify(userRepository, never()).save(any(User.class));
		verify(userCommunityRepository, never()).save(any(UserCommunity.class));
	}

	@Test
	@DisplayName("Should mark expired invitation as expired")
	void testActivateInvitation_MarkExpiredAsExpired() {
		// Arrange
		String keycloakUserId = "keycloak-user-123";
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		LocalDateTime expiredAt = now.minusDays(1);

		Invitation invitation = Invitation.builder()
			.id(UUID.randomUUID())
			.type(InvitationType.STAFF)
			.community(community)
			.communityId(communityId)
			.email("user@example.com")
			.keycloakUserId(keycloakUserId)
			.status(InvitationStatus.PENDING)
			.invitedAt(now.minusDays(8))
			.expiresAt(expiredAt)
			.build();

		when(invitationRepository.findActivePendingAdminInvitation(eq(keycloakUserId), eq(communityId), eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
			.thenReturn(Optional.empty());
		when(invitationRepository.findPendingByKeycloakUserIdAndType(eq(keycloakUserId), eq(InvitationType.STAFF), eq(InvitationStatus.PENDING), any(LocalDateTime.class)))
			.thenReturn(Optional.of(invitation));
		when(invitationRepository.save(any(Invitation.class)))
			.thenAnswer(inv -> inv.getArgument(0));

		// Act
		inviteService.activateInvitation(keycloakUserId, communityId, "user@example.com");

		// Assert
		ArgumentCaptor<Invitation> invitationCaptor = ArgumentCaptor.forClass(Invitation.class);
		verify(invitationRepository).save(invitationCaptor.capture());
		assertThat(invitationCaptor.getValue()
			.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
	}
}
