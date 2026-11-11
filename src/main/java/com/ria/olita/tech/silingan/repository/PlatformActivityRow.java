package com.ria.olita.tech.silingan.repository;

import java.time.LocalDateTime;
import java.util.UUID;

/** Minimal, token-free view of a timestamped record used to build the derived activity feed. */
public record PlatformActivityRow(UUID sourceId, UUID communityId, String communityName, LocalDateTime occurredAt) {
}
