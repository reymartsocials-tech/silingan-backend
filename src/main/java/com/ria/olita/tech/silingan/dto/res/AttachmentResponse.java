package com.ria.olita.tech.silingan.dto.res;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AttachmentResponse {

	private UUID id;
	private String originalFileName;
	private String fileName;
	private String contentType;
	private Long fileSize;
	private String filePath;
	private String publicUrl;
}
