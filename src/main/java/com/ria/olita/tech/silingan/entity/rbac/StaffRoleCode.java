package com.ria.olita.tech.silingan.entity.rbac;

public enum StaffRoleCode {
	COMMUNITY_ADMIN("Community Admin", "Full access to all community-scoped features"),
	PMO_STAFF("PMO Staff", "Operations-focused access for community management"),
	SECURITY_ADMIN("Security Admin", "Security and monitoring access"),
	MAINTENANCE_ADMIN("Maintenance Admin", "Maintenance and facilities management access"),
	READ_ONLY_STAFF("Read-Only Staff", "View-only access to assigned community modules");

	private final String displayName;
	private final String description;

	StaffRoleCode(String displayName, String description) {
		this.displayName = displayName;
		this.description = description;
	}

	public String displayName() {
		return displayName;
	}

	public String description() {
		return description;
	}
}
