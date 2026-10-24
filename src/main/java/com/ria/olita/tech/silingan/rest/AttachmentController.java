package com.ria.olita.tech.silingan.rest;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ria.olita.tech.silingan.dto.res.ApiResponse;
import com.ria.olita.tech.silingan.dto.res.AttachmentResponse;
import com.ria.olita.tech.silingan.entity.Attachment;
import com.ria.olita.tech.silingan.service.AttachmentService;
import com.ria.olita.tech.silingan.service.attachment.AttachmentUploadService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Attachment", description = "File attachment management APIs")
public class AttachmentController {

	private final AttachmentUploadService uploadService;
	private final AttachmentService attachmentService;

	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Operation(
		summary = "Upload an image file",
		description = "Upload an image file and optionally link it to an entity (Announcement or Report). " +
			"Only image files are accepted. Currently uses mock storage. " +
			"If ownerType and ownerId are provided, the attachment will be automatically linked to the entity."
	)
	public ResponseEntity<ApiResponse<AttachmentResponse>> uploadFile(
		@Parameter(description = "Image file to upload (JPEG, PNG, GIF, WebP, SVG, BMP, TIFF)") @RequestParam("file") MultipartFile file,
		@Parameter(description = "Owner type (ANNOUNCEMENT or REPORT). Optional - if provided with ownerId, attachment will be automatically linked.") @RequestParam(value = "ownerType", required = false) String ownerType,
		@Parameter(description = "Owner entity ID (announcement ID or report/issue ID). Required if ownerType is provided.") @RequestParam(value = "ownerId", required = false) UUID ownerId,
		@Parameter(description = "Display order for the attachment. Defaults to 0. Only used if ownerType and ownerId are provided.") @RequestParam(value = "displayOrder", required = false) Integer displayOrder
	) {
		try {
			if (file.isEmpty()) {
				return ResponseEntity.badRequest()
					.body(ApiResponse.error("File is empty"));
			}

			String originalFileName = file.getOriginalFilename();
			String contentType = file.getContentType();
			long fileSize = file.getSize();

			log.info("Uploading image file: {} ({})", originalFileName, contentType);

			var uploadResult = uploadService.uploadFile(
				file.getInputStream(),
				originalFileName,
				contentType,
				fileSize
			);

			var result = attachmentService.uploadAndLinkAttachment(
				uploadResult,
				originalFileName,
				contentType,
				fileSize,
				ownerType,
				ownerId,
				displayOrder
			);

			Attachment attachment = attachmentService.getAttachmentByFileName(result.getStoredFileName())
				.orElseThrow(() -> new IllegalStateException("Attachment not found after creation"));

			AttachmentResponse response = AttachmentResponse.builder()
				.id(attachment.getId())
				.originalFileName(attachment.getOriginalFileName())
				.fileName(attachment.getFileName())
				.contentType(attachment.getContentType())
				.fileSize(attachment.getFileSize())
				.filePath(attachment.getFilePath())
				.publicUrl(result.getPublicUrl())
				.build();

			log.info("Image file uploaded successfully: {} with ID: {}", originalFileName, attachment.getId());

			return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success("Image uploaded successfully", response));

		} catch (Exception e) {
			log.error("Error uploading image file", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiResponse.error("Error uploading image file: " + e.getMessage()));
		}
	}
}
