package com.ria.olita.tech.silingan.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Raw invitation token submitted for a pre-authentication community preview.
 *
 * <p>The token travels in the request body rather than the query string so it is not captured by
 * browser history, proxies, gateways or access logs.
 */
@Builder
@Schema(description = "Resolve a scanned join token to its community")
public record CommunityJoinInvitationPreviewRequest(

	@NotBlank(message = "Invitation token is required")
	@Schema(description = "Raw token taken from the scanned QR code or join link")
	String token
) {
}
