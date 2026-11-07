package com.ria.olita.tech.silingan.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ria.olita.tech.silingan.entity.DirectoryContact;

/**
 * Every lookup is community scoped by signature. There is deliberately no plain {@code findById}
 * helper, so a contact can never be loaded without proving which community it belongs to.
 */
public interface DirectoryContactRepository
	extends JpaRepository<DirectoryContact, UUID>, JpaSpecificationExecutor<DirectoryContact> {

	/** Staff lookup: any status, but only within the caller's community. */
	Optional<DirectoryContact> findByIdAndCommunityId(UUID id, UUID communityId);

	/** Resident lookup: the community and active filters are part of the query itself. */
	Optional<DirectoryContact> findByIdAndCommunityIdAndActiveTrue(UUID id, UUID communityId);
}
