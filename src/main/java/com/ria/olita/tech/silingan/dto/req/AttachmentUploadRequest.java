package com.ria.olita.tech.silingan.dto.req;

import java.util.UUID;

import com.ria.olita.tech.silingan.entity.AttachmentOwnerType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AttachmentUploadRequest {

	@Schema(description = "Owner type (ANNOUNCEMENT or REPORT). Optional - if provided, attachment will be automatically linked.")
	private AttachmentOwnerType ownerType;

	@Schema(description = "Owner entity ID (announcement ID or report/issue ID). Required if ownerType is provided.")
	private UUID ownerId;

	@Schema(description = "Display order for the attachment. Defaults to 0. Only used if ownerType and ownerId are provided.")
	private Integer displayOrder;

	public boolean shouldLink() {
		return ownerType != null && ownerId != null;
	}

	public Integer getDisplayOrderOrDefault() {
		return displayOrder != null ? displayOrder : 0;
	}
}
