package com.ria.olita.tech.silingan.dto.res;

import java.util.Set;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

public record EffectivePermissionsResponse(
	UUID userId,
	UUID communityId,
	StaffRoleCode roleCode,
	Set<PermissionEnum> permissions
) {
}
