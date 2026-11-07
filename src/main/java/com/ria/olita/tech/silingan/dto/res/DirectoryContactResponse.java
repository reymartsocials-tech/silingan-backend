package com.ria.olita.tech.silingan.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.DirectoryContactCategory;
import com.ria.olita.tech.silingan.entity.DirectoryContactType;

import lombok.Builder;

/** Staff view of a directory contact, including management state and audit timestamps. */
@Builder
public record DirectoryContactResponse(
	UUID id,
	UUID communityId,
	DirectoryContactCategory category,
	DirectoryContactType contactType,
	String displayName,
	String primaryPhoneNumber,
	String secondaryPhoneNumber,
	String description,
	String operatingHours,
	String address,
	String email,
	int displayOrder,
	boolean active,
	LocalDateTime createdAt,
	LocalDateTime updatedAt,
	UUID createdBy,
	UUID updatedBy
) {
}
