package com.ria.olita.tech.silingan.entity;

import lombok.Getter;

/**
 * The kind of service a directory contact represents. Modelled as an enum to match the rest of the
 * domain (see {@link AnnouncementCategory}), which has no configurable reference-data mechanism.
 *
 * <p>{@link #OTHER} is the escape hatch for community-approved contacts that do not fit a
 * predefined type, so a community is never blocked from recording a contact.
 */
@Getter
public enum DirectoryContactType {

	PMO("PMO"),
	SECURITY("Security"),
	MAINTENANCE("Maintenance"),
	POLICE("Police"),
	FIRE_DEPARTMENT("Fire Department"),
	AMBULANCE("Ambulance"),
	BARANGAY_EMERGENCY_RESPONSE("Barangay Emergency Response"),
	COMMUNITY_SECURITY("Community Security"),
	WATER_PROVIDER("Water Provider"),
	ELECTRICITY_PROVIDER("Electricity Provider"),
	INTERNET_PROVIDER("Internet Provider"),
	OTHER("Other");

	private final String label;

	DirectoryContactType(String label) {
		this.label = label;
	}
}
