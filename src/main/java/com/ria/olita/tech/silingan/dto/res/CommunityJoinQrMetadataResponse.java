package com.ria.olita.tech.silingan.dto.res;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Metadata backing the community join QR code, for admins that need to display or share the join
 * link alongside the rendered image.
 */
@Builder
public record CommunityJoinQrMetadataResponse(
	@Schema(example = "5ee38760-cefd-4f0a-a8d2-4941c77fc792") UUID communityId,
	@Schema(example = "Maple Residences") String communityName,
	@Schema(example = "MAPLERES-2026") String communityCode,
	@Schema(example = "https://app.silingan.ph/join?communityCode=MAPLERES-2026") String joinUrl,
	@Schema(example = "/api/v1/communities/5ee38760-cefd-4f0a-a8d2-4941c77fc792/join-qr") String qrCodeUrl
) {
}
