package com.ria.olita.tech.silingan.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * Inline QR image. Rendered on demand and never persisted.
 */
@Builder
@Schema(description = "QR code image returned inline for immediate display or download")
public record QrCodeImageResponse(

	@Schema(example = "image/png")
	String contentType,

	@Schema(description = "Suggested download file name", example = "silingan-maple-residences-join.png")
	String fileName,

	@Schema(description = "Base64-encoded image bytes", example = "iVBORw0KGgo...")
	String base64
) {
}
