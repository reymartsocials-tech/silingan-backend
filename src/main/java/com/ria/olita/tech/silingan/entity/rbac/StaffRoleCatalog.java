package com.ria.olita.tech.silingan.entity.rbac;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The immutable, code-owned definition of the predefined staff roles and the permission matrix.
 *
 * <p>For MVP the catalogue is read-only: custom roles, custom permissions, and per-community
 * overrides are not supported. Keeping the matrix in code (rather than in editable tables) is what
 * makes that guarantee enforceable — there is no write path that can diverge a community from it.
 */
public final class StaffRoleCatalog {

	private static final Map<StaffRoleCode, Map<Domain, AccessLevel>> MATRIX = buildMatrix();
	private static final Map<StaffRoleCode, Set<PermissionEnum>> PERMISSIONS = buildPermissions();

	private StaffRoleCatalog() {
	}

	/** Roles in display order; {@link StaffRoleCode#COMMUNITY_ADMIN} is first and highest access. */
	public static List<StaffRoleCode> roles() {
		return List.of(StaffRoleCode.values());
	}

	/** Modules rendered as rows of the permission matrix. */
	public static List<Domain> modules() {
		return List.of(Domain.values());
	}

	public static AccessLevel accessLevel(StaffRoleCode roleCode, Domain module) {
		return MATRIX.getOrDefault(roleCode, Map.of()).getOrDefault(module, AccessLevel.NO_ACCESS);
	}

	public static Map<Domain, AccessLevel> accessLevels(StaffRoleCode roleCode) {
		return MATRIX.getOrDefault(roleCode, Map.of());
	}

	/** The permission set a role grants, derived from its matrix row. */
	public static Set<PermissionEnum> permissions(StaffRoleCode roleCode) {
		return PERMISSIONS.getOrDefault(roleCode, Set.of());
	}

	public static boolean isHighestAccess(StaffRoleCode roleCode) {
		return roleCode == StaffRoleCode.COMMUNITY_ADMIN;
	}

	private static Map<StaffRoleCode, Map<Domain, AccessLevel>> buildMatrix() {
		EnumMap<StaffRoleCode, Map<Domain, AccessLevel>> matrix = new EnumMap<>(StaffRoleCode.class);

		matrix.put(StaffRoleCode.COMMUNITY_ADMIN, row(
			AccessLevel.VIEW_AND_MANAGE,
			AccessLevel.VIEW_AND_MANAGE,
			AccessLevel.VIEW_AND_MANAGE,
			AccessLevel.VIEW_AND_MANAGE,
			AccessLevel.VIEW_AND_MANAGE,
			AccessLevel.VIEW_AND_MANAGE,
			AccessLevel.VIEW_AND_MANAGE
		));
		matrix.put(StaffRoleCode.PMO_STAFF, row(
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_AND_MANAGE,
			AccessLevel.VIEW_AND_MANAGE,
			AccessLevel.VIEW_AND_MANAGE,
			AccessLevel.NO_ACCESS
		));
		matrix.put(StaffRoleCode.SECURITY_ADMIN, row(
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_AND_MANAGE,
			AccessLevel.VIEW_ONLY,
			AccessLevel.NO_ACCESS
		));
		matrix.put(StaffRoleCode.MAINTENANCE_ADMIN, row(
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_AND_MANAGE,
			AccessLevel.VIEW_ONLY,
			AccessLevel.NO_ACCESS
		));
		matrix.put(StaffRoleCode.READ_ONLY_STAFF, row(
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_ONLY,
			AccessLevel.VIEW_ONLY,
			AccessLevel.NO_ACCESS
		));

		return Collections.unmodifiableMap(matrix);
	}

	private static Map<Domain, AccessLevel> row(
		AccessLevel community,
		AccessLevel resident,
		AccessLevel staff,
		AccessLevel announcement,
		AccessLevel report,
		AccessLevel directory,
		AccessLevel settings
	) {
		EnumMap<Domain, AccessLevel> levels = new EnumMap<>(Domain.class);
		levels.put(Domain.COMMUNITY, community);
		levels.put(Domain.RESIDENT, resident);
		levels.put(Domain.STAFF, staff);
		levels.put(Domain.ANNOUNCEMENT, announcement);
		levels.put(Domain.REPORT, report);
		levels.put(Domain.DIRECTORY, directory);
		levels.put(Domain.SETTINGS, settings);
		return Collections.unmodifiableMap(levels);
	}

	private static Map<StaffRoleCode, Set<PermissionEnum>> buildPermissions() {
		EnumMap<StaffRoleCode, Set<PermissionEnum>> permissions = new EnumMap<>(StaffRoleCode.class);

		for (StaffRoleCode roleCode : StaffRoleCode.values()) {
			EnumSet<PermissionEnum> granted = EnumSet.noneOf(PermissionEnum.class);
			MATRIX.getOrDefault(roleCode, Map.of()).forEach((module, level) -> {
				if (level.canView()) {
					granted.add(PermissionEnum.of(module, Action.VIEW));
				}
				if (level.canManage()) {
					granted.add(PermissionEnum.of(module, Action.MANAGE));
				}
			});
			permissions.put(roleCode, Collections.unmodifiableSet(granted));
		}

		return Collections.unmodifiableMap(permissions);
	}
}
