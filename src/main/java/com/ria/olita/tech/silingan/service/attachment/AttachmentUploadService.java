package com.ria.olita.tech.silingan.service.attachment;

import java.io.InputStream;

/**
 * Abstraction layer for file upload operations.
 * Future implementation will be backed by Azure Blob Storage.
 * Currently prepared with contract methods for upload, download, and delete operations.
 */
public interface AttachmentUploadService {

	/**
	 * Uploads a file and returns metadata including storage path.
	 *
	 * @param fileStream the input stream of the file to upload
	 * @param fileName the original file name
	 * @param contentType the MIME type of the file
	 * @param fileSize the size of the file in bytes
	 * @return AttachmentUploadResult containing file metadata
	 */
	AttachmentUploadResult uploadFile(InputStream fileStream, String fileName, String contentType, Long fileSize);

	/**
	 * Downloads a file from storage.
	 *
	 * @param filePath the storage path/URL of the file
	 * @return an InputStream for reading the file content
	 */
	InputStream downloadFile(String filePath);

	/**
	 * Deletes a file from storage.
	 *
	 * @param filePath the storage path/URL of the file
	 */
	void deleteFile(String filePath);

	/**
	 * Generates a public URL for accessing the file (if applicable for the storage provider).
	 *
	 * @param filePath the storage path/URL of the file
	 * @return the public URL to access the file
	 */
	String generatePublicUrl(String filePath);
}
