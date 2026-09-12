package com.ria.olita.tech.silingan.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.ria.olita.tech.silingan.dto.req.AssignStaffRoleRequest;
import com.ria.olita.tech.silingan.dto.res.AvailablePermissionsResponse;
import com.ria.olita.tech.silingan.dto.res.CurrentUserCapabilitiesResponse;
import com.ria.olita.tech.silingan.dto.res.EffectivePermissionsResponse;
import com.ria.olita.tech.silingan.dto.res.PermissionMatrixResponse;
import com.ria.olita.tech.silingan.dto.res.StaffRoleAssignmentResponse;
import com.ria.olita.tech.silingan.dto.res.StaffRoleResponse;
import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

public interface CommunityRbacService {

	AvailablePermissionsResponse getPermissionCatalog();

	List<StaffRoleResponse> getRoleCatalog(UUID communityId);

	PermissionMatrixResponse getPermissionMatrix(UUID communityId);

	StaffRoleAssignmentResponse assignStaffRole(UUID communityId, UUID userId, AssignStaffRoleRequest request);

	StaffRoleAssignmentResponse getStaffRoleAssignment(UUID communityId, UUID userId);

	EffectivePermissionsResponse getEffectivePermissions(UUID communityId, UUID userId);

	CurrentUserCapabilitiesResponse getCurrentUserCapabilities(UUID communityId);

	Set<PermissionEnum> resolveEffectivePermissions(UUID userId, UUID communityId);

	Optional<StaffRoleCode> resolveAssignedRole(UUID userId, UUID communityId);
}
