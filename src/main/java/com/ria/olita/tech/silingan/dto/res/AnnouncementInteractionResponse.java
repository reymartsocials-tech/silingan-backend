package com.ria.olita.tech.silingan.dto.res;

import java.util.UUID;

import lombok.Builder;

/**
 * Complete interaction state of one announcement from the perspective of the authenticated
 * resident. Returned by both the acknowledge and the helpful operation so a client can refresh
 * both buttons and both totals from a single response. It never exposes who interacted.
 */
@Builder
public record AnnouncementInteractionResponse(
	UUID announcementId,
	boolean acknowledged,
	boolean helpful,
	long acknowledgementCount,
	long helpfulCount
) {
}
