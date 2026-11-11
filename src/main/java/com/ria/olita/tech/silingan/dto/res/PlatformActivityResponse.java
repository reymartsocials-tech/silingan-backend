package com.ria.olita.tech.silingan.dto.res;

import java.time.Instant;
import java.util.UUID;

/** The id is {@code TYPE:sourceRecordId}, stable across requests. Only the community name is described. */
public record PlatformActivityResponse(
	String id,
	PlatformActivityType type,
	String title,
	String description,
	Instant occurredAt,
	UUID communityId
) {
}
