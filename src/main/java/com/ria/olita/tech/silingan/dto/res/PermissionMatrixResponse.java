package com.ria.olita.tech.silingan.dto.res;

import java.util.List;

import com.ria.olita.tech.silingan.entity.rbac.AccessLevel;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

/**
 * The permission matrix: predefined roles as columns, modules as rows.
 */
public record PermissionMatrixResponse(
	List<MatrixRole> roles,
	List<MatrixRow> modules
) {
	public record MatrixRole(
		StaffRoleCode roleCode,
		String name,
		boolean highestAccess
	) {
	}

	public record MatrixRow(
		String module,
		String label,
		List<MatrixCell> access
	) {
	}

	public record MatrixCell(
		StaffRoleCode roleCode,
		AccessLevel accessLevel,
		String label
	) {
	}
}
