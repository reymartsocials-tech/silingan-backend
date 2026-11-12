package com.ria.olita.tech.silingan.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Response returned once, when a join invitation is created.
 *
 * <p>This is the only time the raw token, the join URL and the QR image are available: only the
 * token hash is persisted, so the join URL cannot be reconstructed later. The administrator must
 * download or share it immediately; a lost invitation is replaced by revoking it and creating a new
 * one.
 */
@Builder
@Schema(description = "Newly created join invitation, including the one-time join URL and QR image")
public record CommunityJoinInvitationCreatedResponse(

	@Schema(description = "Invitation metadata")
	CommunityJoinInvitationResponse invitation,

	@Schema(
		description = "One-time join URL containing the raw token. Not recoverable after this response.",
		example = "https://app.silingan.ph/join?token=Y8hVfM3wQk7xP2nR6cT9"
	)
	String joinUrl,

	@Schema(description = "QR code encoding the join URL")
	QrCodeImageResponse qrCode
) {
}
