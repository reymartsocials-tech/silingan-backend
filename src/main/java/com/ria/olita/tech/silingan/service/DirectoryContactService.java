package com.ria.olita.tech.silingan.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ria.olita.tech.silingan.dto.req.CreateDirectoryContactRequest;
import com.ria.olita.tech.silingan.dto.req.DirectoryContactFilterCriteria;
import com.ria.olita.tech.silingan.dto.req.UpdateDirectoryContactRequest;
import com.ria.olita.tech.silingan.dto.res.DirectoryContactResponse;
import com.ria.olita.tech.silingan.dto.res.ResidentDirectoryContactResponse;

/**
 * Community directory and emergency contacts. Staff operations are scoped to a community the
 * caller may manage; resident operations are scoped to the caller's active membership.
 */
public interface DirectoryContactService {

	DirectoryContactResponse createContact(CreateDirectoryContactRequest request);

	Page<DirectoryContactResponse> getContacts(UUID communityId, DirectoryContactFilterCriteria criteria, Pageable pageable);

	DirectoryContactResponse getContactById(UUID contactId);

	DirectoryContactResponse updateContact(UUID contactId, UpdateDirectoryContactRequest request);

	DirectoryContactResponse activateContact(UUID contactId);

	DirectoryContactResponse deactivateContact(UUID contactId);

	void deleteContact(UUID contactId);

	List<ResidentDirectoryContactResponse> getContactsForResident();

	ResidentDirectoryContactResponse getContactForResident(UUID contactId);
}
