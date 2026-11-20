package com.ria.olita.tech.silingan.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Response returned once, when a join invitation is created.
 *
 * <p>This is the only time the raw token, the join URL and the QR image are available: only the
 * token hash is persisted, so the join target cannot be reconstructed later. The administrator must
 * download or share it immediately; a lost invitation is replaced by revoking it and creating a new
 * one.
 *
 * <p>While link distribution is disabled the QR encodes a SILINGAN app deep link and no shareable
 * web link is returned, so the invitation can only be used by scanning or uploading the QR inside
 * the installed app.
 */
@Builder
@Schema(description = "Newly created join invitation, including the one-time QR image and, when enabled, the join URL")
public record CommunityJoinInvitationCreatedResponse(

	@Schema(description = "Invitation metadata")
	CommunityJoinInvitationResponse invitation,

	@Schema(
		description = "One-time shareable join URL containing the raw token. Null while link distribution "
			+ "is disabled, where the QR is in-app only. Not recoverable after this response.",
		example = "https://app.silingan.ph/join?token=Y8hVfM3wQk7xP2nR6cT9",
		nullable = true
	)
	String joinUrl,

	@Schema(
		description = "Whether this invitation may be shared as a web link. When false the QR is only "
			+ "usable by scanning or uploading it inside the installed SILINGAN app.",
		example = "false"
	)
	boolean linkDistributionEnabled,

	@Schema(description = "QR code encoding the join target")
	QrCodeImageResponse qrCode
) {
}
