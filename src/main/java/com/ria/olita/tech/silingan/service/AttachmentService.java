package com.ria.olita.tech.silingan.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.Attachment;
import com.ria.olita.tech.silingan.entity.AttachmentOwnerType;
import com.ria.olita.tech.silingan.entity.AttachmentReference;
import com.ria.olita.tech.silingan.service.attachment.AttachmentUploadResult;

public interface AttachmentService {

	Attachment createAttachment(String originalFileName, String fileName, String contentType, Long fileSize, String filePath);

	Optional<Attachment> getAttachmentById(UUID attachmentId);

	Optional<Attachment> getAttachmentByFileName(String fileName);

	void deleteAttachment(UUID attachmentId);

	AttachmentReference linkAttachmentToOwner(UUID attachmentId, AttachmentOwnerType ownerType, UUID ownerId, Integer displayOrder);

	List<AttachmentReference> getAttachmentsByOwner(AttachmentOwnerType ownerType, UUID ownerId);

	void unlinkAttachment(UUID attachmentReferenceId);

	void unlinkAllAttachmentsFromOwner(AttachmentOwnerType ownerType, UUID ownerId);

	void updateAttachmentDisplayOrder(UUID attachmentReferenceId, Integer displayOrder);

	AttachmentUploadResult uploadAndLinkAttachment(AttachmentUploadResult uploadResult, String originalFileName, String contentType, Long fileSize, String ownerType, UUID ownerId, Integer displayOrder);
}
