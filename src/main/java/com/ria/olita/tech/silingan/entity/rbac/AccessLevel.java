package com.ria.olita.tech.silingan.entity.rbac;

/**
 * How much access a predefined staff role has over one module, as rendered in the permission
 * matrix.
 */
public enum AccessLevel {
	VIEW_AND_MANAGE("View + Manage"),
	VIEW_ONLY("View Only"),
	NO_ACCESS("No Access");

	private final String label;

	AccessLevel(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}

	public boolean canView() {
		return this != NO_ACCESS;
	}

	public boolean canManage() {
		return this == VIEW_AND_MANAGE;
	}
}
