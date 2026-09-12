package com.ria.olita.tech.silingan.entity.rbac;

import lombok.Getter;

/**
 * The modules shown as rows of the permission matrix. One domain maps to exactly one
 * {@code view}/{@code manage} permission pair.
 */
@Getter
public enum Domain {
	COMMUNITY("community", "Community"),
	RESIDENT("resident", "Residents"),
	STAFF("staff", "Staff"),
	ANNOUNCEMENT("announcement", "Announcements"),
	REPORT("report", "Reports"),
	DIRECTORY("directory", "Directory"),
	SETTINGS("settings", "Settings");

	private final String value;
	private final String label;

	Domain(String value, String label) {
		this.value = value;
		this.label = label;
	}
}
