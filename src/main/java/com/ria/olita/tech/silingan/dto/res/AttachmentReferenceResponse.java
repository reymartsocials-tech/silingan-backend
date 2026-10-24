package com.ria.olita.tech.silingan.dto.res;

import java.util.UUID;

import com.ria.olita.tech.silingan.entity.AttachmentOwnerType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AttachmentReferenceResponse {

	private UUID id;
	private AttachmentResponse attachment;
	private AttachmentOwnerType ownerType;
	private UUID ownerId;
	private Integer displayOrder;
}
