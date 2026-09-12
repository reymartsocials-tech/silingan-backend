package com.ria.olita.tech.silingan.dto.res;

import java.util.Set;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

public record CurrentUserCommunityPermissionsResponse(
	UUID communityId,
	String communityCode,
	String communityName,
	boolean selected,
	StaffRoleCode roleCode,
	Set<PermissionEnum> permissions
) {
}
