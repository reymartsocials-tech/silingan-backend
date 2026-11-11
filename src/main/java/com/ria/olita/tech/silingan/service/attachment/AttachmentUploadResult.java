package com.ria.olita.tech.silingan.service.attachment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a file upload operation.
 * Contains metadata returned by the storage provider.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AttachmentUploadResult {

	/**
	 * The storage path or blob URL where the file is stored.
	 * For Azure Blob Storage, this would be the blob URL or container path.
	 * For mock implementations, this is a dummy URL.
	 */
	private String filePath;

	/**
	 * The generated/stored file name (may differ from original).
	 */
	private String storedFileName;

	/**
	 * The file size in bytes.
	 */
	private Long fileSize;

	/**
	 * The MIME type of the file.
	 */
	private String contentType;

	/**
	 * Public URL to access the file, if applicable.
	 */
	private String publicUrl;
}
