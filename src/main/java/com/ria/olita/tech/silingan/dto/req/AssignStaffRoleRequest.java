package com.ria.olita.tech.silingan.dto.req;

import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

import jakarta.validation.constraints.NotNull;

public record AssignStaffRoleRequest(
	@NotNull StaffRoleCode roleCode,
	Boolean active
) {
	public AssignStaffRoleRequest {
		if (active == null) {
			active = true;
		}
	}
}
