package com.ria.olita.tech.silingan.repository;

import java.util.UUID;

/**
 * Projection for grouped interaction totals keyed by announcement.
 */
public interface AnnouncementInteractionCount {

	UUID getAnnouncementId();

	long getTotal();
}
