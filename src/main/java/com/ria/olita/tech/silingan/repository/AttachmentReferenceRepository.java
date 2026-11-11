package com.ria.olita.tech.silingan.repository;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ria.olita.tech.silingan.entity.AttachmentOwnerType;
import com.ria.olita.tech.silingan.entity.AttachmentReference;

@Repository
public interface AttachmentReferenceRepository extends JpaRepository<AttachmentReference, UUID> {

	@Query("SELECT ar FROM AttachmentReference ar WHERE ar.ownerType = ?1 AND ar.ownerId = ?2 ORDER BY ar.displayOrder ASC")
	List<AttachmentReference> findByOwnerTypeAndOwnerId(AttachmentOwnerType ownerType, UUID ownerId);

	/**
	 * Batch variant used when building a page of owners, so attachments are resolved in one
	 * query instead of one per row.
	 */
	@Query("SELECT ar FROM AttachmentReference ar JOIN FETCH ar.attachment " +
		"WHERE ar.ownerType = ?1 AND ar.ownerId IN ?2 ORDER BY ar.displayOrder ASC")
	List<AttachmentReference> findByOwnerTypeAndOwnerIdIn(AttachmentOwnerType ownerType, Collection<UUID> ownerIds);

	@Query("SELECT ar FROM AttachmentReference ar WHERE ar.attachmentId = ?1")
	List<AttachmentReference> findByAttachmentId(UUID attachmentId);

	@Query("DELETE FROM AttachmentReference ar WHERE ar.ownerType = ?1 AND ar.ownerId = ?2")
	void deleteByOwnerTypeAndOwnerId(AttachmentOwnerType ownerType, UUID ownerId);
}
