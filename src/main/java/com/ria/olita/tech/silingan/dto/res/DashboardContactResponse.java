package com.ria.olita.tech.silingan.dto.res;

import java.util.UUID;

import com.ria.olita.tech.silingan.entity.DirectoryContactCategory;
import com.ria.olita.tech.silingan.entity.DirectoryContactType;

public record DashboardContactResponse(
	UUID id,
	String displayName,
	DirectoryContactCategory category,
	DirectoryContactType contactType,
	String primaryPhoneNumber,
	String operatingHours,
	boolean emergency
) {
}
