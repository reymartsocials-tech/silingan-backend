package com.ria.olita.tech.silingan.service.attachment;

import java.io.InputStream;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Mock implementation of AttachmentUploadService.
 * Uses dummy URLs for file paths - designed to be replaced by Azure Blob Storage implementation.
 * All operations are no-ops or return mock data.
 */
@Service
public class MockAttachmentUploadService implements AttachmentUploadService {

	private static final String MOCK_BLOB_CONTAINER_URL = "https://storage.azure.com/silingan-attachments/";

	@Override
	public AttachmentUploadResult uploadFile(InputStream fileStream, String fileName, String contentType, Long fileSize) {
		String storedFileName = generateStoredFileName(fileName);
		String filePath = MOCK_BLOB_CONTAINER_URL + storedFileName;

		return AttachmentUploadResult.builder()
			.filePath(filePath)
			.storedFileName(storedFileName)
			.fileSize(fileSize)
			.contentType(contentType)
			.publicUrl(filePath)
			.build();
	}

	@Override
	public InputStream downloadFile(String filePath) {
		return null;
	}

	@Override
	public void deleteFile(String filePath) {
	}

	@Override
	public String generatePublicUrl(String filePath) {
		return filePath;
	}

	/**
	 * Generates a unique stored file name using UUID to avoid collisions.
	 */
	private String generateStoredFileName(String originalFileName) {
		String extension = getFileExtension(originalFileName);
		return UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
	}

	private String getFileExtension(String fileName) {
		int lastDotIndex = fileName.lastIndexOf('.');
		if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
			return fileName.substring(lastDotIndex + 1);
		}
		return "";
	}
}
