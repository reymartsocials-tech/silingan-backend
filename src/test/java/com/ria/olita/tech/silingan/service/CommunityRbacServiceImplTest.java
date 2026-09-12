package com.ria.olita.tech.silingan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.ria.olita.tech.silingan.dto.res.PermissionMatrixResponse;
import com.ria.olita.tech.silingan.dto.res.StaffRoleResponse;
import com.ria.olita.tech.silingan.entity.UserCommunityStaffRole;
import com.ria.olita.tech.silingan.entity.rbac.AccessLevel;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityStaffRoleRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.security.scope.CommunityScopeGuard;
import com.ria.olita.tech.silingan.security.scope.StaffGrantGuard;
import com.ria.olita.tech.silingan.service.impl.CommunityRbacServiceImpl;

class CommunityRbacServiceImplTest {

	private final UserCommunityStaffRoleRepository staffRoleAssignmentRepository =
		Mockito.mock(UserCommunityStaffRoleRepository.class);
	private final UserCommunityRepository userCommunityRepository = Mockito.mock(UserCommunityRepository.class);
	private final UserRepository userRepository = Mockito.mock(UserRepository.class);
	private final CommunityRepository communityRepository = Mockito.mock(CommunityRepository.class);

	private final CommunityRbacServiceImpl service = new CommunityRbacServiceImpl(
		staffRoleAssignmentRepository,
		userCommunityRepository,
		userRepository,
		communityRepository,
		new StaffGrantGuard(new CommunityScopeGuard(userCommunityRepository))
	);

	@Test
	void roleCatalogReturnsTheFivePredefinedRolesWithCommunityAdminAsHighestAccess() {
		UUID communityId = UUID.randomUUID();
		Mockito.when(communityRepository.existsById(communityId)).thenReturn(true);

		List<StaffRoleResponse> roles = service.getRoleCatalog(communityId);

		assertThat(roles).extracting(StaffRoleResponse::roleCode).containsExactly(
			StaffRoleCode.COMMUNITY_ADMIN,
			StaffRoleCode.PMO_STAFF,
			StaffRoleCode.SECURITY_ADMIN,
			StaffRoleCode.MAINTENANCE_ADMIN,
			StaffRoleCode.READ_ONLY_STAFF
		);
		assertThat(roles).allSatisfy(role -> {
			assertThat(role.name()).isNotBlank();
			assertThat(role.description()).isNotBlank();
		});
		assertThat(roles)
			.filteredOn(StaffRoleResponse::highestAccess)
			.extracting(StaffRoleResponse::roleCode)
			.containsExactly(StaffRoleCode.COMMUNITY_ADMIN);
	}

	@Test
	void roleCatalogFailsForUnknownCommunity() {
		UUID communityId = UUID.randomUUID();
		Mockito.when(communityRepository.existsById(communityId)).thenReturn(false);

		assertThatThrownBy(() -> service.getRoleCatalog(communityId))
			.isInstanceOf(NotFoundException.class);
	}

	@Test
	void permissionMatrixExposesRolesAsColumnsAndModulesAsRows() {
		UUID communityId = UUID.randomUUID();
		Mockito.when(communityRepository.existsById(communityId)).thenReturn(true);

		PermissionMatrixResponse matrix = service.getPermissionMatrix(communityId);

		assertThat(matrix.roles()).hasSize(StaffRoleCode.values().length);
		assertThat(matrix.modules()).hasSize(Domain.values().length);
		assertThat(matrix.modules())
			.allSatisfy(row -> assertThat(row.access()).hasSize(StaffRoleCode.values().length));

		assertThat(cell(matrix, Domain.SETTINGS, StaffRoleCode.COMMUNITY_ADMIN)).isEqualTo(AccessLevel.VIEW_AND_MANAGE);
		assertThat(cell(matrix, Domain.SETTINGS, StaffRoleCode.PMO_STAFF)).isEqualTo(AccessLevel.NO_ACCESS);
		assertThat(cell(matrix, Domain.ANNOUNCEMENT, StaffRoleCode.PMO_STAFF)).isEqualTo(AccessLevel.VIEW_AND_MANAGE);
		assertThat(cell(matrix, Domain.REPORT, StaffRoleCode.SECURITY_ADMIN)).isEqualTo(AccessLevel.VIEW_AND_MANAGE);
		assertThat(cell(matrix, Domain.COMMUNITY, StaffRoleCode.READ_ONLY_STAFF)).isEqualTo(AccessLevel.VIEW_ONLY);
	}

	@Test
	void effectivePermissionsComeFromTheAssignedPredefinedRoleOnly() {
		UUID communityId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Mockito.when(staffRoleAssignmentRepository.findByUserIdAndCommunityIdAndActiveTrue(userId, communityId))
			.thenReturn(Optional.of(UserCommunityStaffRole.builder()
				.roleCode(StaffRoleCode.PMO_STAFF)
				.build()));

		assertThat(service.resolveEffectivePermissions(userId, communityId))
			.containsExactlyInAnyOrder(
				PermissionEnum.COMMUNITY_VIEW,
				PermissionEnum.RESIDENT_VIEW,
				PermissionEnum.STAFF_VIEW,
				PermissionEnum.ANNOUNCEMENT_VIEW,
				PermissionEnum.ANNOUNCEMENT_MANAGE,
				PermissionEnum.REPORT_VIEW,
				PermissionEnum.REPORT_MANAGE,
				PermissionEnum.DIRECTORY_VIEW,
				PermissionEnum.DIRECTORY_MANAGE
			);
	}

	@Test
	void userWithoutAnActiveRoleHasNoPermissions() {
		UUID communityId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Mockito.when(staffRoleAssignmentRepository.findByUserIdAndCommunityIdAndActiveTrue(userId, communityId))
			.thenReturn(Optional.empty());

		assertThat(service.resolveEffectivePermissions(userId, communityId)).isEmpty();
	}

	private AccessLevel cell(PermissionMatrixResponse matrix, Domain module, StaffRoleCode roleCode) {
		return matrix.modules().stream()
			.filter(row -> row.module().equals(module.getValue()))
			.flatMap(row -> row.access().stream())
			.filter(access -> access.roleCode() == roleCode)
			.map(PermissionMatrixResponse.MatrixCell::accessLevel)
			.findFirst()
			.orElseThrow();
	}
}
