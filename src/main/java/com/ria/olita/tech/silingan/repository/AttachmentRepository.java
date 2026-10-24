package com.ria.olita.tech.silingan.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ria.olita.tech.silingan.entity.Attachment;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

	Optional<Attachment> findByFileName(String fileName);

	Optional<Attachment> findByOriginalFileName(String originalFileName);
}
