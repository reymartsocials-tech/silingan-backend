package com.ria.olita.tech.silingan.dto.res;

import java.util.Set;

import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

/**
 * A predefined staff role as shown in the role catalogue. Read-only for MVP.
 */
public record StaffRoleResponse(
	StaffRoleCode roleCode,
	String name,
	String description,
	boolean highestAccess,
	Set<PermissionEnum> permissions
) {
}
