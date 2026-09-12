package com.ria.olita.tech.silingan.entity.rbac;

public enum PermissionEnum {

	COMMUNITY_VIEW(Domain.COMMUNITY, Action.VIEW),
	COMMUNITY_MANAGE(Domain.COMMUNITY, Action.MANAGE),

	RESIDENT_VIEW(Domain.RESIDENT, Action.VIEW),
	RESIDENT_MANAGE(Domain.RESIDENT, Action.MANAGE),

	STAFF_VIEW(Domain.STAFF, Action.VIEW),
	STAFF_MANAGE(Domain.STAFF, Action.MANAGE),

	ANNOUNCEMENT_VIEW(Domain.ANNOUNCEMENT, Action.VIEW),
	ANNOUNCEMENT_MANAGE(Domain.ANNOUNCEMENT, Action.MANAGE),

	REPORT_VIEW(Domain.REPORT, Action.VIEW),
	REPORT_MANAGE(Domain.REPORT, Action.MANAGE),

	DIRECTORY_VIEW(Domain.DIRECTORY, Action.VIEW),
	DIRECTORY_MANAGE(Domain.DIRECTORY, Action.MANAGE),

	SETTINGS_VIEW(Domain.SETTINGS, Action.VIEW),
	SETTINGS_MANAGE(Domain.SETTINGS, Action.MANAGE);

	private final Domain domain;
	private final Action action;

	PermissionEnum(Domain domain, Action action) {
		this.domain = domain;
		this.action = action;
	}

	public Domain getDomain() {
		return domain;
	}

	public Action getAction() {
		return action;
	}

	public String getValue() {
		return domain.getValue() + ":" + action.value();
	}

	public static PermissionEnum of(Domain domain, Action action) {
		for (PermissionEnum permission : values()) {
			if (permission.domain == domain && permission.action == action) {
				return permission;
			}
		}
		throw new IllegalArgumentException("No permission for " + domain + ":" + action);
	}
}
