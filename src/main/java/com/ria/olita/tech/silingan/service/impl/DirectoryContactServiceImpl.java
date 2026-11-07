package com.ria.olita.tech.silingan.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.req.CreateDirectoryContactRequest;
import com.ria.olita.tech.silingan.dto.req.DirectoryContactFilterCriteria;
import com.ria.olita.tech.silingan.dto.req.UpdateDirectoryContactRequest;
import com.ria.olita.tech.silingan.dto.res.DirectoryContactResponse;
import com.ria.olita.tech.silingan.dto.res.ResidentDirectoryContactResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.DirectoryContact;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.mapper.DirectoryContactMapper;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.DirectoryContactRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.spec.DirectoryContactSpecifications;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.security.scope.CommunityScopeGuard;
import com.ria.olita.tech.silingan.service.DirectoryContactService;

import lombok.RequiredArgsConstructor;

/**
 * Directory and emergency contact management.
 *
 * <p>Community isolation is enforced twice: the caller's community is resolved from the security
 * context and verified against an active membership, and every contact lookup is then scoped to
 * that community in the query itself. Changing a contact identifier therefore yields "not found"
 * rather than another community's data.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DirectoryContactServiceImpl implements DirectoryContactService {

	private static final int STAFF_MAX_PAGE_SIZE = 100;
	private static final int STAFF_DEFAULT_PAGE_SIZE = 20;

	private final DirectoryContactRepository directoryContactRepository;
	private final CommunityRepository communityRepository;
	private final UserCommunityRepository userCommunityRepository;
	private final CommunityScopeGuard communityScopeGuard;
	private final DirectoryContactMapper directoryContactMapper;

	// --- staff -------------------------------------------------------------------------------

	@Override
	@Transactional
	public DirectoryContactResponse createContact(CreateDirectoryContactRequest request) {
		if (request == null) {
			throw new ValidationException("Request body is required");
		}

		// Authorization is settled before the community is looked up, so an unauthorized caller
		// cannot use the response to probe which communities exist.
		requireContext();
		if (request.communityId() == null) {
			throw new ValidationException("Community ID is required");
		}
		requireManageAccess(request.communityId());
		Community community = requireActiveCommunity(request.communityId());

		DirectoryContact contact = directoryContactMapper.toEntity(request);
		contact.setDisplayName(requireText(request.displayName(), "Display name"));
		contact.setPrimaryPhoneNumber(requireText(request.primaryPhoneNumber(), "Primary phone number"));
		contact.setSecondaryPhoneNumber(trimToNull(request.secondaryPhoneNumber()));
		contact.setEmail(trimToNull(request.email()));
		contact.setDisplayOrder(requireDisplayOrder(request.displayOrder()));
		// New contacts are published to residents unless the client explicitly asks otherwise.
		contact.setActive(request.active() == null || request.active());
		assignCommunity(contact, community);

		return directoryContactMapper.toResponse(directoryContactRepository.save(contact));
	}

	@Override
	public Page<DirectoryContactResponse> getContacts(
		UUID communityId,
		DirectoryContactFilterCriteria criteria,
		Pageable pageable
	) {
		UUID scopedCommunityId = resolveStaffCommunity(communityId);
		requireViewAccess(scopedCommunityId);

		return directoryContactRepository
			.findAll(DirectoryContactSpecifications.forStaff(scopedCommunityId, criteria), toStaffPageable(pageable))
			.map(directoryContactMapper::toResponse);
	}

	@Override
	public DirectoryContactResponse getContactById(UUID contactId) {
		UUID communityId = resolveStaffCommunity(null);
		requireViewAccess(communityId);

		return directoryContactMapper.toResponse(requireContact(contactId, communityId));
	}

	@Override
	@Transactional
	public DirectoryContactResponse updateContact(UUID contactId, UpdateDirectoryContactRequest request) {
		if (request == null) {
			throw new ValidationException("Request body is required");
		}

		UUID communityId = resolveStaffCommunity(null);
		requireManageAccess(communityId);

		DirectoryContact contact = requireContact(contactId, communityId);
		// The community association is deliberately left untouched: a contact cannot be moved.
		contact.setDisplayName(requireText(request.displayName(), "Display name"));
		contact.setCategory(requireValue(request.category(), "Category"));
		contact.setContactType(requireValue(request.contactType(), "Contact type"));
		contact.setPrimaryPhoneNumber(requireText(request.primaryPhoneNumber(), "Primary phone number"));
		contact.setSecondaryPhoneNumber(trimToNull(request.secondaryPhoneNumber()));
		contact.setDescription(trimToNull(request.description()));
		contact.setOperatingHours(trimToNull(request.operatingHours()));
		contact.setAddress(trimToNull(request.address()));
		contact.setEmail(trimToNull(request.email()));
		contact.setDisplayOrder(requireDisplayOrder(request.displayOrder()));

		return directoryContactMapper.toResponse(directoryContactRepository.save(contact));
	}

	@Override
	@Transactional
	public DirectoryContactResponse activateContact(UUID contactId) {
		return changeActiveState(contactId, true);
	}

	@Override
	@Transactional
	public DirectoryContactResponse deactivateContact(UUID contactId) {
		return changeActiveState(contactId, false);
	}

	@Override
	@Transactional
	public void deleteContact(UUID contactId) {
		UUID communityId = resolveStaffCommunity(null);
		requireManageAccess(communityId);

		DirectoryContact contact = requireContact(contactId, communityId);
		// Soft delete keeps the audit trail; the entity restriction hides it from every later read.
		contact.setDeleted(true);
		directoryContactRepository.save(contact);
	}

	// --- resident ----------------------------------------------------------------------------

	@Override
	public List<ResidentDirectoryContactResponse> getContactsForResident() {
		UserCommunity membership = resolveResidentActiveMembership();

		return directoryContactRepository
			.findAll(DirectoryContactSpecifications.forResident(membership.getCommunityId()))
			.stream()
			.map(directoryContactMapper::toResidentResponse)
			.toList();
	}

	@Override
	public ResidentDirectoryContactResponse getContactForResident(UUID contactId) {
		UserCommunity membership = resolveResidentActiveMembership();
		if (contactId == null) {
			throw new ValidationException("Contact ID is required");
		}

		// Inactive, deleted and other-community contacts are all excluded by the query, so they are
		// indistinguishable from a contact that never existed.
		return directoryContactRepository
			.findByIdAndCommunityIdAndActiveTrue(contactId, membership.getCommunityId())
			.map(directoryContactMapper::toResidentResponse)
			.orElseThrow(() -> new NotFoundException("Directory contact not found"));
	}

	// --- helpers -----------------------------------------------------------------------------

	private DirectoryContactResponse changeActiveState(UUID contactId, boolean active) {
		UUID communityId = resolveStaffCommunity(null);
		requireManageAccess(communityId);

		DirectoryContact contact = requireContact(contactId, communityId);
		if (contact.isActive() == active) {
			return directoryContactMapper.toResponse(contact);
		}

		contact.setActive(active);
		return directoryContactMapper.toResponse(directoryContactRepository.save(contact));
	}

	private DirectoryContact requireContact(UUID contactId, UUID communityId) {
		if (contactId == null) {
			throw new ValidationException("Contact ID is required");
		}

		return directoryContactRepository.findByIdAndCommunityId(contactId, communityId)
			.orElseThrow(() -> new NotFoundException("Directory contact not found"));
	}

	/**
	 * A community supplied by the client is honoured only after the membership check below; when it
	 * is absent the caller's active community is used.
	 */
	private UUID resolveStaffCommunity(UUID requestedCommunityId) {
		if (requestedCommunityId != null) {
			return requestedCommunityId;
		}

		UserContext context = requireContext();
		UUID activeCommunityId = parseCommunityId(context.communityId());
		if (activeCommunityId == null) {
			throw new ForbiddenException("Community scope could not be resolved for this request");
		}
		return activeCommunityId;
	}

	private void requireManageAccess(UUID communityId) {
		UserContext context = requireContext();
		communityScopeGuard.assertAccess(communityId);

		if (!context.canManage(Domain.DIRECTORY, communityId)) {
			throw new ForbiddenException("You are not allowed to manage directory contacts for this community");
		}
	}

	private void requireViewAccess(UUID communityId) {
		UserContext context = requireContext();
		communityScopeGuard.assertAccess(communityId);

		if (!context.canView(Domain.DIRECTORY, communityId)) {
			throw new ForbiddenException("You are not allowed to view directory contacts for this community");
		}
	}

	/**
	 * Resolves the resident's active membership from the database. The community claim in the token
	 * only selects which membership to verify; it never grants access on its own.
	 */
	private UserCommunity resolveResidentActiveMembership() {
		UserContext context = requireContext();

		UUID communityId = parseCommunityId(context.communityId());
		if (communityId == null) {
			throw new ForbiddenException("No active community membership");
		}

		return userCommunityRepository
			.findByUserIdAndCommunityIdAndUserStatusActive(parseCurrentUserId(context), communityId)
			.filter(membership -> membership.getRole() != null)
			.orElseThrow(() -> new ForbiddenException("No active community membership"));
	}

	/**
	 * Ordering is fixed by the specification, so any client-supplied sort is dropped rather than
	 * passed through as an unvalidated property name. The page size is capped.
	 */
	private Pageable toStaffPageable(Pageable pageable) {
		if (pageable == null || pageable.isUnpaged()) {
			return PageRequest.of(0, STAFF_DEFAULT_PAGE_SIZE, Sort.unsorted());
		}

		int pageSize = Math.min(Math.max(pageable.getPageSize(), 1), STAFF_MAX_PAGE_SIZE);
		return PageRequest.of(pageable.getPageNumber(), pageSize, Sort.unsorted());
	}

	private Community requireActiveCommunity(UUID communityId) {
		if (communityId == null) {
			throw new ValidationException("Community ID is required");
		}
		return communityRepository.findByIdAndStatus(communityId, CommunityStatus.ACTIVE)
			.orElseThrow(() -> new NotFoundException("Community not found"));
	}

	private void assignCommunity(DirectoryContact contact, Community community) {
		contact.setCommunity(community);
		contact.setCommunityId(community.getId());
	}

	private int requireDisplayOrder(Integer displayOrder) {
		if (displayOrder == null) {
			return DirectoryContact.DEFAULT_DISPLAY_ORDER;
		}
		if (displayOrder < 0) {
			throw new ValidationException("Display order must not be negative");
		}
		return displayOrder;
	}

	private <T> T requireValue(T value, String field) {
		if (value == null) {
			throw new ValidationException(field + " is required");
		}
		return value;
	}

	private String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new ValidationException(field + " must not be blank");
		}
		return value.trim();
	}

	private String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private UserContext requireContext() {
		UserContext context = UserContextHolder.get();
		if (context == null || context.userId() == null) {
			throw new ForbiddenException("No authenticated user context");
		}
		return context;
	}

	private UUID parseCommunityId(String communityId) {
		if (communityId == null) {
			return null;
		}
		try {
			return UUID.fromString(communityId);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private UUID parseCurrentUserId(UserContext context) {
		try {
			return UUID.fromString(context.userId());
		} catch (IllegalArgumentException ex) {
			throw new ForbiddenException("Invalid authenticated user context");
		}
	}
}
