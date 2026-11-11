package com.ria.olita.tech.silingan.dto.res;

import java.util.UUID;

import com.ria.olita.tech.silingan.entity.DirectoryContactCategory;
import com.ria.olita.tech.silingan.entity.DirectoryContactType;

import lombok.Builder;

/**
 * Resident view of a directory contact. Community ownership, audit users, soft-delete state and
 * active status are intentionally absent: a resident only ever receives active contacts from their
 * own community, so those fields carry no useful information and would leak internal state.
 */
@Builder
public record ResidentDirectoryContactResponse(
	UUID id,
	DirectoryContactCategory category,
	DirectoryContactType contactType,
	String displayName,
	String primaryPhoneNumber,
	String secondaryPhoneNumber,
	String description,
	String operatingHours,
	String address
) {
}
