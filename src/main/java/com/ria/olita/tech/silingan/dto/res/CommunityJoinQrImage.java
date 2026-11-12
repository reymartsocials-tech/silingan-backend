package com.ria.olita.tech.silingan.dto.res;

import lombok.Builder;

/**
 * Rendered community join QR code. The PNG is generated per request and never persisted.
 */
@Builder
public record CommunityJoinQrImage(
	String fileName,
	byte[] content
) {
}
