package com.ria.olita.tech.silingan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.ria.olita.tech.silingan.dto.res.CurrentUserCommunityPermissionsResponse;
import com.ria.olita.tech.silingan.dto.res.CurrentUserResponse;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserStatus;
import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.security.scope.CommunityScopeGuard;
import com.ria.olita.tech.silingan.security.scope.StaffGrantGuard;
import com.ria.olita.tech.silingan.service.impl.CurrentUserServiceImpl;

class CurrentUserServiceImplTest {

	private final UserRepository userRepository = Mockito.mock(UserRepository.class);
	private final CommunityRbacService communityRbacService = Mockito.mock(CommunityRbacService.class);
	private final UserCommunityRepository userCommunityRepository = Mockito.mock(UserCommunityRepository.class);
	private final CommunityRepository communityRepository = Mockito.mock(CommunityRepository.class);

	private final CurrentUserServiceImpl service = new CurrentUserServiceImpl(
		userRepository,
		communityRbacService,
		new StaffGrantGuard(new CommunityScopeGuard(userCommunityRepository)),
		communityRepository
	);

	@AfterEach
	void tearDown() {
		UserContextHolder.clear();
	}

	@Test
	void returnsProfileRealmRolesAndSelectedCommunityPermissions() {
		UUID userId = UUID.randomUUID();
		UUID selectedCommunityId = UUID.randomUUID();
		UUID otherCommunityId = UUID.randomUUID();

		UserContextHolder.set(UserContext.builder()
			.userId(userId.toString())
			.keycloakUserId("kc-123")
			.communityId(selectedCommunityId.toString())
			.roles(List.of(SilinganRealmRole.STAFF))
			.build());

		Mockito.when(userRepository.findById(userId))
			.thenReturn(Optional.of(User.builder()
				.id(userId)
				.keycloakUserId("kc-123")
				.username("juan")
				.email("juan@example.com")
				.mobileNumber("09171234567")
				.firstName("Juan")
				.lastName("Dela Cruz")
				.status(UserStatus.ACTIVE)
				.build()));

		Mockito.when(communityRbacService.getCurrentUserCommunities())
			.thenReturn(List.of(
				new CurrentUserCommunityPermissionsResponse(
					otherCommunityId, "COMM-2", "Community Two", false,
					StaffRoleCode.READ_ONLY_STAFF, Set.of(PermissionEnum.STAFF_VIEW)),
				new CurrentUserCommunityPermissionsResponse(
					selectedCommunityId, "COMM-1", "Community One", true,
					StaffRoleCode.PMO_STAFF, Set.of(PermissionEnum.ANNOUNCEMENT_MANAGE))));
		Mockito.when(communityRepository.findActiveCommunityNameMapByUserId(userId))
			.thenReturn(Map.of(
				selectedCommunityId, "Community One",
				otherCommunityId, "Community Two"));

		CurrentUserResponse response = service.getCurrentUser();

		assertThat(response.userId()).isEqualTo(userId);
		assertThat(response.keycloakUserId()).isEqualTo("kc-123");
		assertThat(response.email()).isEqualTo("juan@example.com");
		assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
		assertThat(response.roles()).containsExactly(SilinganRealmRole.STAFF);
		assertThat(response.selectedCommunityId()).isEqualTo(selectedCommunityId);
		assertThat(response.roleCode()).isEqualTo(StaffRoleCode.PMO_STAFF);
		assertThat(response.permissions()).containsExactly(PermissionEnum.ANNOUNCEMENT_MANAGE);
		assertThat(response.communities()).hasSize(2);
	}

	@Test
	void returnsEmptyPermissionsWhenNoCommunityIsSelected() {
		UUID userId = UUID.randomUUID();

		UserContextHolder.set(UserContext.builder()
			.userId(userId.toString())
			.keycloakUserId("kc-456")
			.roles(List.of(SilinganRealmRole.RESIDENT))
			.build());

		Mockito.when(userRepository.findById(userId))
			.thenReturn(Optional.of(User.builder()
				.id(userId)
				.keycloakUserId("kc-456")
				.status(UserStatus.ACTIVE)
				.build()));
		Mockito.when(communityRbacService.getCurrentUserCommunities())
			.thenReturn(List.of());

		CurrentUserResponse response = service.getCurrentUser();

		assertThat(response.selectedCommunityId()).isNull();
		assertThat(response.roleCode()).isNull();
		assertThat(response.permissions()).isEmpty();
		assertThat(response.communities()).isEmpty();
		assertThat(response.roles()).containsExactly(SilinganRealmRole.RESIDENT);
	}

	@Test
	void rejectsRequestWithoutUserContext() {
		assertThatThrownBy(service::getCurrentUser)
			.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void failsWhenLocalUserRowIsMissing() {
		UUID userId = UUID.randomUUID();
		UserContextHolder.set(UserContext.builder()
			.userId(userId.toString())
			.roles(List.of(SilinganRealmRole.RESIDENT))
			.build());
		Mockito.when(userRepository.findById(userId))
			.thenReturn(Optional.empty());

		assertThatThrownBy(service::getCurrentUser)
			.isInstanceOf(NotFoundException.class);
	}
}
