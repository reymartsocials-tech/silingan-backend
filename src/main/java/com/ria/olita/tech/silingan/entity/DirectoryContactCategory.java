package com.ria.olita.tech.silingan.entity;

import lombok.Getter;

/**
 * Groups a directory contact and, through {@link #getDisplayPriority()}, fixes the order in which
 * the groups are presented. Emergency services sort first so that residents reach them without
 * scrolling past routine contacts.
 */
@Getter
public enum DirectoryContactCategory {

	EMERGENCY_SERVICE(0, "Emergency Services"),
	COMMUNITY_SERVICE(1, "Community Services"),
	UTILITY_PROVIDER(2, "Utility Providers"),
	OTHER_SERVICE(3, "Other Services");

	private final int displayPriority;
	private final String label;

	DirectoryContactCategory(int displayPriority, String label) {
		this.displayPriority = displayPriority;
		this.label = label;
	}

	public boolean isEmergency() {
		return this == EMERGENCY_SERVICE;
	}
}
