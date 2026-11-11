package com.ria.olita.tech.silingan.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ria.olita.tech.silingan.dto.req.CreateStaffAccountRequest;
import com.ria.olita.tech.silingan.dto.req.EditStaffInfoRequest;
import com.ria.olita.tech.silingan.dto.res.StaffAccountResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.UserStatus;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.exception.ConflictException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.service.KeycloakService;

@ExtendWith(MockitoExtension.class)
@DisplayName("StaffAccountService Tests")
class StaffAccountServiceImplTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private CommunityRepository communityRepository;

	@Mock
	private UserCommunityRepository userCommunityRepository;

	@Mock
	private KeycloakService keycloakService;

	@InjectMocks
	private StaffAccountServiceImpl staffAccountService;

	private UUID communityId;
	private UUID staffUserId;
	private Community community;
	private User staffUser;
	private UserCommunity userCommunity;

	@BeforeEach
	void setUp() {
		communityId = UUID.randomUUID();
		staffUserId = UUID.randomUUID();

		community = Community.builder()
			.id(communityId)
			.name("Test Community")
			.communityCode("TEST-001")
			.build();

		staffUser = User.builder()
			.id(staffUserId)
			.keycloakUserId("keycloak-123")
			.username("juandelacruz")
			.firstName("Juan")
			.lastName("Dela Cruz")
			.email("juan@example.com")
			.mobileNumber("+639171234567")
			.status(UserStatus.ACTIVE)
			.build();

		userCommunity = UserCommunity.builder()
			.id(UUID.randomUUID())
			.user(staffUser)
			.community(community)
			.role(SilinganRealmRole.STAFF)
			.roleCode(StaffRoleCode.PMO_STAFF)
			.build();
	}

	@Test
	@DisplayName("Should create staff account successfully")
	void testCreateStaffAccount_Success() {
		CreateStaffAccountRequest request = new CreateStaffAccountRequest(
			"Juan",
			"Dela Cruz",
			"juan@example.com",
			"+639171234567",
			StaffRoleCode.PMO_STAFF,
			"TEST-001"
		);

		when(communityRepository.existsById(communityId)).thenReturn(true);
		when(userRepository.existsByEmailAndDeletedFalse(anyString())).thenReturn(false);
		when(userRepository.existsByMobileNumberAndDeletedFalse(anyString())).thenReturn(false);
		when(communityRepository.findById(communityId)).thenReturn(Optional.of(community));
		when(keycloakService.createUser(any(), eq(communityId))).thenReturn("keycloak-123");
		when(userRepository.saveAndFlush(any(User.class))).thenReturn(staffUser);

		StaffAccountResponse response = staffAccountService.createStaffAccount(communityId, request);

		assertNotNull(response);
		assertEquals(staffUser.getFirstName(), response.firstName());
		assertEquals(staffUser.getLastName(), response.lastName());
		assertEquals(StaffRoleCode.PMO_STAFF, response.staffRole());
		assertEquals(UserStatus.ACTIVE, response.status());
		verify(keycloakService, times(1)).createUser(any(), eq(communityId));
		verify(userRepository, times(1)).saveAndFlush(any(User.class));
	}

	@Test
	@DisplayName("Should throw NotFoundException when community not found during creation")
	void testCreateStaffAccount_CommunityNotFound() {
		CreateStaffAccountRequest request = new CreateStaffAccountRequest(
			"Juan",
			"Dela Cruz",
			"juan@example.com",
			"+639171234567",
			StaffRoleCode.PMO_STAFF,
			"TEST-001"
		);

		when(communityRepository.existsById(communityId)).thenReturn(false);

		assertThrows(NotFoundException.class, () -> staffAccountService.createStaffAccount(communityId, request));
	}

	@Test
	@DisplayName("Should throw ConflictException when email already exists during creation")
	void testCreateStaffAccount_EmailAlreadyExists() {
		CreateStaffAccountRequest request = new CreateStaffAccountRequest(
			"Juan",
			"Dela Cruz",
			"juan@example.com",
			"+639171234567",
			StaffRoleCode.PMO_STAFF,
			"TEST-001"
		);

		when(communityRepository.existsById(communityId)).thenReturn(true);
		when(userRepository.existsByEmailAndDeletedFalse(anyString())).thenReturn(true);

		assertThrows(ConflictException.class, () -> staffAccountService.createStaffAccount(communityId, request));
	}

	@Test
	@DisplayName("Should edit staff information successfully")
	void testEditStaffInfo_Success() {
		EditStaffInfoRequest request = new EditStaffInfoRequest(
			"Miguel",
			"Santos",
			"+639189876543"
		);

		when(userRepository.findById(staffUserId)).thenReturn(Optional.of(staffUser));
		when(userCommunityRepository.findByUserIdAndCommunityId(staffUserId, communityId))
			.thenReturn(Optional.of(userCommunity));
		when(userRepository.existsByMobileNumberAndDeletedFalse(anyString())).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class))).thenReturn(staffUser);

		StaffAccountResponse response = staffAccountService.editStaffInfo(communityId, staffUserId, request);

		assertNotNull(response);
		verify(userRepository, times(1)).saveAndFlush(any(User.class));
		verify(keycloakService, times(1)).updateUserAttributes(eq(staffUser.getKeycloakUserId()), anyMap());
	}

	@Test
	@DisplayName("Should throw NotFoundException when staff user not found during edit")
	void testEditStaffInfo_UserNotFound() {
		EditStaffInfoRequest request = new EditStaffInfoRequest(
			"Miguel",
			"Santos",
			"+639189876543"
		);

		when(userRepository.findById(staffUserId)).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class, 
			() -> staffAccountService.editStaffInfo(communityId, staffUserId, request));
	}

	@Test
	@DisplayName("Should throw ConflictException when new email already exists during edit")
	void testEditStaffInfo_EmailAlreadyExists() {
		EditStaffInfoRequest request = new EditStaffInfoRequest(
			"Miguel",
			"Santos",
			"+639189876543"
		);

		when(userRepository.findById(staffUserId)).thenReturn(Optional.of(staffUser));
		when(userCommunityRepository.findByUserIdAndCommunityId(staffUserId, communityId))
			.thenReturn(Optional.of(userCommunity));
		when(userRepository.existsByMobileNumberAndDeletedFalse(anyString())).thenReturn(true);

		assertThrows(ConflictException.class, 
			() -> staffAccountService.editStaffInfo(communityId, staffUserId, request));
	}

	@Test
	@DisplayName("Should deactivate staff account successfully")
	void testDeactivateStaff_Success() {
		when(userRepository.findById(staffUserId)).thenReturn(Optional.of(staffUser));
		when(userCommunityRepository.findByUserIdAndCommunityId(staffUserId, communityId))
			.thenReturn(Optional.of(userCommunity));
		when(userRepository.saveAndFlush(any(User.class))).thenReturn(staffUser);

		StaffAccountResponse response = staffAccountService.deactivateStaff(communityId, staffUserId);

		assertNotNull(response);
		assertEquals(UserStatus.INACTIVE, response.status());
		verify(userRepository, times(1)).saveAndFlush(any(User.class));
	}

	@Test
	@DisplayName("Should throw IllegalStateException when deactivating last community admin")
	void testDeactivateStaff_LastCommunityAdmin() {
		staffUser.setStatus(UserStatus.ACTIVE);
		userCommunity.setRole(SilinganRealmRole.COMMUNITY_ADMIN);

		when(userRepository.findById(staffUserId)).thenReturn(Optional.of(staffUser));
		when(userCommunityRepository.findByUserIdAndCommunityId(staffUserId, communityId))
			.thenReturn(Optional.of(userCommunity));
		when(userCommunityRepository.countByCommunityIdAndRoleAndUserStatus(
			communityId, 
			SilinganRealmRole.COMMUNITY_ADMIN,
			UserStatus.ACTIVE
		)).thenReturn(1);

		assertThrows(IllegalStateException.class, 
			() -> staffAccountService.deactivateStaff(communityId, staffUserId),
			"Cannot deactivate the final Community Admin");
	}

	@Test
	@DisplayName("Should successfully deactivate staff when not last community admin")
	void testDeactivateStaff_NotLastAdmin() {
		staffUser.setStatus(UserStatus.ACTIVE);
		userCommunity.setRole(SilinganRealmRole.STAFF);

		when(userRepository.findById(staffUserId)).thenReturn(Optional.of(staffUser));
		when(userCommunityRepository.findByUserIdAndCommunityId(staffUserId, communityId))
			.thenReturn(Optional.of(userCommunity));
		when(userCommunityRepository.countByCommunityIdAndRoleAndUserStatus(
			communityId, 
			SilinganRealmRole.COMMUNITY_ADMIN,
			UserStatus.ACTIVE
		)).thenReturn(2);
		when(userRepository.saveAndFlush(any(User.class))).thenReturn(staffUser);

		StaffAccountResponse response = staffAccountService.deactivateStaff(communityId, staffUserId);

		assertNotNull(response);
		assertEquals(UserStatus.INACTIVE, response.status());
		verify(userRepository, times(1)).saveAndFlush(any(User.class));
	}

	@Test
	@DisplayName("Should activate staff account successfully")
	void testActivateStaff_Success() {
		staffUser.setStatus(UserStatus.INACTIVE);

		when(userRepository.findById(staffUserId)).thenReturn(Optional.of(staffUser));
		when(userCommunityRepository.findByUserIdAndCommunityId(staffUserId, communityId))
			.thenReturn(Optional.of(userCommunity));
		when(userRepository.saveAndFlush(any(User.class))).thenReturn(staffUser);

		StaffAccountResponse response = staffAccountService.activateStaff(communityId, staffUserId);

		assertNotNull(response);
		assertEquals(UserStatus.ACTIVE, response.status());
		verify(userRepository, times(1)).saveAndFlush(any(User.class));
	}

	@Test
	@DisplayName("Should throw NotFoundException when staff user not found during activation")
	void testActivateStaff_UserNotFound() {
		when(userRepository.findById(staffUserId)).thenReturn(Optional.empty());

		assertThrows(NotFoundException.class, 
			() -> staffAccountService.activateStaff(communityId, staffUserId));
	}

	@Test
	@DisplayName("Should validate staff member exists in community during edit")
	void testEditStaffInfo_StaffNotInCommunity() {
		EditStaffInfoRequest request = new EditStaffInfoRequest(
			"Miguel",
			"Santos",
			"+639189876543"
		);

		when(userRepository.findById(staffUserId)).thenReturn(Optional.of(staffUser));
		when(userCommunityRepository.findByUserIdAndCommunityId(staffUserId, communityId))
			.thenReturn(Optional.empty());

		assertThrows(NotFoundException.class, 
			() -> staffAccountService.editStaffInfo(communityId, staffUserId, request));
	}

	@Test
	@DisplayName("Should allow mobile update when new mobile is different from current")
	void testEditStaffInfo_DifferentMobileAllowed() {
		EditStaffInfoRequest request = new EditStaffInfoRequest(
			"Juan",
			"Dela Cruz",
			"+639189876543"
		);

		when(userRepository.findById(staffUserId)).thenReturn(Optional.of(staffUser));
		when(userCommunityRepository.findByUserIdAndCommunityId(staffUserId, communityId))
			.thenReturn(Optional.of(userCommunity));
		when(userRepository.existsByMobileNumberAndDeletedFalse("+639189876543")).thenReturn(false);
		when(userRepository.saveAndFlush(any(User.class))).thenReturn(staffUser);

		StaffAccountResponse response = staffAccountService.editStaffInfo(communityId, staffUserId, request);

		assertNotNull(response);
		verify(userRepository, times(1)).saveAndFlush(any(User.class));
	}
}
