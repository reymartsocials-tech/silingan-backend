package com.ria.olita.tech.silingan.service.impl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.entity.Attachment;
import com.ria.olita.tech.silingan.entity.AttachmentOwnerType;
import com.ria.olita.tech.silingan.entity.AttachmentReference;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.repository.AttachmentReferenceRepository;
import com.ria.olita.tech.silingan.repository.AttachmentRepository;
import com.ria.olita.tech.silingan.service.AttachmentService;
import com.ria.olita.tech.silingan.service.attachment.AttachmentUploadResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AttachmentServiceImpl implements AttachmentService {

	private final AttachmentRepository attachmentRepository;
	private final AttachmentReferenceRepository attachmentReferenceRepository;

	private static final Set<String> ALLOWED_IMAGE_TYPES = new HashSet<>(Arrays.asList(
		"image/jpeg",
		"image/jpg",
		"image/png",
		"image/gif",
		"image/webp",
		"image/svg+xml",
		"image/bmp",
		"image/tiff"
	));

	@Override
	public Attachment createAttachment(String originalFileName, String fileName, String contentType, Long fileSize, String filePath) {
		Attachment attachment = Attachment.builder()
			.originalFileName(originalFileName)
			.fileName(fileName)
			.contentType(contentType)
			.fileSize(fileSize)
			.filePath(filePath)
			.build();
		return attachmentRepository.save(attachment);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Attachment> getAttachmentById(UUID attachmentId) {
		return attachmentRepository.findById(attachmentId);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Attachment> getAttachmentByFileName(String fileName) {
		return attachmentRepository.findByFileName(fileName);
	}

	@Override
	public void deleteAttachment(UUID attachmentId) {
		attachmentRepository.deleteById(attachmentId);
	}

	@Override
	public AttachmentReference linkAttachmentToOwner(UUID attachmentId, AttachmentOwnerType ownerType, UUID ownerId, Integer displayOrder) {
		Attachment attachment = attachmentRepository.findById(attachmentId)
			.orElseThrow(() -> new IllegalArgumentException("Attachment not found: " + attachmentId));

		AttachmentReference reference = AttachmentReference.builder()
			.attachment(attachment)
			.ownerType(ownerType)
			.ownerId(ownerId)
			.displayOrder(displayOrder)
			.build();
		return attachmentReferenceRepository.save(reference);
	}

	@Override
	@Transactional(readOnly = true)
	public List<AttachmentReference> getAttachmentsByOwner(AttachmentOwnerType ownerType, UUID ownerId) {
		return attachmentReferenceRepository.findByOwnerTypeAndOwnerId(ownerType, ownerId);
	}

	@Override
	public void unlinkAttachment(UUID attachmentReferenceId) {
		attachmentReferenceRepository.deleteById(attachmentReferenceId);
	}

	@Override
	public void unlinkAllAttachmentsFromOwner(AttachmentOwnerType ownerType, UUID ownerId) {
		attachmentReferenceRepository.deleteByOwnerTypeAndOwnerId(ownerType, ownerId);
	}

	@Override
	public void updateAttachmentDisplayOrder(UUID attachmentReferenceId, Integer displayOrder) {
		AttachmentReference reference = attachmentReferenceRepository.findById(attachmentReferenceId)
			.orElseThrow(() -> new IllegalArgumentException("Attachment reference not found: " + attachmentReferenceId));
		reference.setDisplayOrder(displayOrder);
		attachmentReferenceRepository.save(reference);
	}

	@Override
	public AttachmentUploadResult uploadAndLinkAttachment(AttachmentUploadResult uploadResult, String originalFileName, String contentType, Long fileSize, String ownerType, UUID ownerId, Integer displayOrder) {
		validateImageType(contentType, originalFileName);

		Attachment attachment = createAttachment(
			originalFileName,
			uploadResult.getStoredFileName(),
			contentType,
			fileSize,
			uploadResult.getFilePath()
		);

		if (ownerType != null && ownerId != null) {
			linkAttachmentToOwnerInternal(attachment.getId(), ownerType, ownerId, displayOrder);
		}

		return AttachmentUploadResult.builder()
			.filePath(uploadResult.getFilePath())
			.storedFileName(uploadResult.getStoredFileName())
			.fileSize(fileSize)
			.contentType(contentType)
			.publicUrl(uploadResult.getPublicUrl())
			.build();
	}

	private void validateImageType(String contentType, String originalFileName) {
		if (!isValidImageType(contentType)) {
			log.warn("Invalid file type attempted: {} for file: {}", contentType, originalFileName);
			throw new ValidationException("Invalid file type. Only image files are accepted. Supported formats: JPEG, PNG, GIF, WebP, SVG, BMP, TIFF");
		}
	}

	private void linkAttachmentToOwnerInternal(UUID attachmentId, String ownerType, UUID ownerId, Integer displayOrder) {
		try {
			AttachmentOwnerType ownerTypeEnum = Enum.valueOf(AttachmentOwnerType.class, ownerType);
			Integer order = displayOrder != null ? displayOrder : 0;
			linkAttachmentToOwner(attachmentId, ownerTypeEnum, ownerId, order);
			log.info("Attachment {} automatically linked to {} {}", attachmentId, ownerType, ownerId);
		} catch (IllegalArgumentException e) {
			log.warn("Invalid owner type: {}", ownerType);
			throw new ValidationException("Invalid owner type. Supported values: ANNOUNCEMENT, REPORT");
		}
	}

	private boolean isValidImageType(String contentType) {
		if (contentType == null || contentType.isBlank()) {
			return false;
		}
		String mimeType = contentType.toLowerCase().split(";")[0].trim();
		return ALLOWED_IMAGE_TYPES.contains(mimeType);
	}
}
