package com.ria.olita.tech.silingan.security.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;

class CommunityScopeGuardTest {

	private final UserCommunityRepository userCommunityRepository = Mockito.mock(UserCommunityRepository.class);
	private final CommunityScopeGuard scopeGuard = new CommunityScopeGuard(userCommunityRepository);
	private final StaffGrantGuard grantGuard = new StaffGrantGuard(scopeGuard);

	@AfterEach
	void tearDown() {
		UserContextHolder.clear();
	}

	private UUID givenCaller(SilinganRealmRole role, UUID tokenCommunityId) {
		UUID userId = UUID.randomUUID();
		UserContextHolder.set(UserContext.builder()
			.userId(userId.toString())
			.communityId(tokenCommunityId == null ? null : tokenCommunityId.toString())
			.roles(List.of(role))
			.build());
		return userId;
	}

	@Test
	void shouldDenyCommunityAdminActingOnAnotherCommunity() {
		UUID ownCommunity = UUID.randomUUID();
		UUID otherCommunity = UUID.randomUUID();
		UUID userId = givenCaller(SilinganRealmRole.COMMUNITY_ADMIN, ownCommunity);

		when(userCommunityRepository.findByUserIdAndCommunityId(userId, otherCommunity))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> scopeGuard.assertAccess(otherCommunity))
			.isInstanceOf(ForbiddenException.class)
			.hasMessageContaining("do not have access to this community");
	}

	@Test
	void shouldAllowCommunityAdminActingOnOwnCommunity() {
		UUID ownCommunity = UUID.randomUUID();
		UUID userId = givenCaller(SilinganRealmRole.COMMUNITY_ADMIN, ownCommunity);

		when(userCommunityRepository.findByUserIdAndCommunityId(userId, ownCommunity))
			.thenReturn(Optional.of(UserCommunity.builder().build()));

		assertThatCode(() -> scopeGuard.assertAccess(ownCommunity)).doesNotThrowAnyException();
	}

	@Test
	void shouldNotTrustTokenCommunityClaimWithoutMembership() {
		UUID spoofed = UUID.randomUUID();
		UUID userId = givenCaller(SilinganRealmRole.STAFF, spoofed);

		when(userCommunityRepository.findByUserIdAndCommunityId(userId, spoofed))
			.thenReturn(Optional.empty());

		assertThat(scopeGuard.hasAccess(spoofed)).isFalse();
	}

	@Test
	void shouldAllowPlatformAdminAnywhere() {
		givenCaller(SilinganRealmRole.PLATFORM_ADMIN, null);

		assertThatCode(() -> scopeGuard.assertAccess(UUID.randomUUID())).doesNotThrowAnyException();
		Mockito.verifyNoInteractions(userCommunityRepository);
	}

	@Test
	void shouldRejectUnauthenticatedCaller() {
		assertThatThrownBy(() -> scopeGuard.assertAccess(UUID.randomUUID()))
			.isInstanceOf(ForbiddenException.class)
			.hasMessageContaining("No authenticated user context");
	}

	@Test
	void shouldBlockGrantingPermissionsCallerDoesNotHold() {
		UUID communityId = UUID.randomUUID();
		UUID userId = givenCaller(SilinganRealmRole.STAFF, communityId);
		when(userCommunityRepository.findByUserIdAndCommunityId(userId, communityId))
			.thenReturn(Optional.of(UserCommunity.builder().build()));

		Set<PermissionEnum> held = EnumSet.of(PermissionEnum.REPORT_VIEW);
		Set<PermissionEnum> requested = EnumSet.of(PermissionEnum.REPORT_VIEW, PermissionEnum.STAFF_MANAGE);

		assertThatThrownBy(() -> grantGuard.assertCanGrant(communityId, requested, () -> held))
			.isInstanceOf(ForbiddenException.class)
			.hasMessageContaining("staff:manage");
	}

	@Test
	void shouldAllowGrantingSubsetOfHeldPermissions() {
		UUID communityId = UUID.randomUUID();
		UUID userId = givenCaller(SilinganRealmRole.STAFF, communityId);
		when(userCommunityRepository.findByUserIdAndCommunityId(userId, communityId))
			.thenReturn(Optional.of(UserCommunity.builder().build()));

		Set<PermissionEnum> held = EnumSet.of(PermissionEnum.REPORT_VIEW, PermissionEnum.REPORT_MANAGE);

		assertThatCode(() -> grantGuard.assertCanGrant(communityId, EnumSet.of(PermissionEnum.REPORT_VIEW), () -> held))
			.doesNotThrowAnyException();
	}

	@Test
	void shouldBlockSelfPrivilegeChange() {
		UUID communityId = UUID.randomUUID();
		UUID userId = givenCaller(SilinganRealmRole.COMMUNITY_ADMIN, communityId);

		assertThatThrownBy(() -> grantGuard.assertNotSelf(userId, "change the staff role"))
			.isInstanceOf(ForbiddenException.class)
			.hasMessageContaining("your own account");
	}

	@Test
	void shouldReserveCommunityAdminRoleForAdministrators() {
		UUID communityId = UUID.randomUUID();
		UUID userId = givenCaller(SilinganRealmRole.STAFF, communityId);
		when(userCommunityRepository.findByUserIdAndCommunityId(userId, communityId))
			.thenReturn(Optional.of(UserCommunity.builder().build()));

		assertThatThrownBy(() -> grantGuard.assertCanAssignRole(
			communityId,
			StaffRoleCode.COMMUNITY_ADMIN,
			EnumSet.allOf(PermissionEnum.class),
			() -> EnumSet.allOf(PermissionEnum.class)
		)).isInstanceOf(ForbiddenException.class);
	}
}
