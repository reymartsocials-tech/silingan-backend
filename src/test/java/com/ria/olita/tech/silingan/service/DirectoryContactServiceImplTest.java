package com.ria.olita.tech.silingan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.ria.olita.tech.silingan.dto.req.CreateDirectoryContactRequest;
import com.ria.olita.tech.silingan.dto.req.DirectoryContactFilterCriteria;
import com.ria.olita.tech.silingan.dto.req.UpdateDirectoryContactRequest;
import com.ria.olita.tech.silingan.dto.res.DirectoryContactResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.DirectoryContact;
import com.ria.olita.tech.silingan.entity.DirectoryContactCategory;
import com.ria.olita.tech.silingan.entity.DirectoryContactType;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.rbac.CommunityAccess;
import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.mapper.DirectoryContactMapperImpl;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.DirectoryContactRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.security.scope.CommunityScopeGuard;
import com.ria.olita.tech.silingan.service.impl.DirectoryContactServiceImpl;

/** Staff-facing directory contact management: authorization, validation and community isolation. */
class DirectoryContactServiceImplTest {

	private final DirectoryContactRepository directoryContactRepository = Mockito.mock(DirectoryContactRepository.class);
	private final CommunityRepository communityRepository = Mockito.mock(CommunityRepository.class);
	private final UserCommunityRepository userCommunityRepository = Mockito.mock(UserCommunityRepository.class);
	private final CommunityScopeGuard communityScopeGuard = Mockito.mock(CommunityScopeGuard.class);

	private final DirectoryContactServiceImpl service = new DirectoryContactServiceImpl(
		directoryContactRepository,
		communityRepository,
		userCommunityRepository,
		communityScopeGuard,
		new DirectoryContactMapperImpl()
	);

	private final UUID communityA = UUID.randomUUID();
	private final UUID communityB = UUID.randomUUID();
	private final UUID staffUserId = UUID.randomUUID();

	@AfterEach
	void tearDown() {
		UserContextHolder.clear();
	}

	// --- fixtures -----------------------------------------------------------------------------

	/** Staff member with full directory permissions in {@code communityId}. */
	private void givenDirectoryManager(UUID communityId) {
		UserContextHolder.set(UserContext.builder()
			.userId(staffUserId.toString())
			.communityId(communityId.toString())
			.roles(List.of(SilinganRealmRole.STAFF))
			.communityAccess(new CommunityAccess(
				communityId,
				List.of(PermissionEnum.DIRECTORY_VIEW, PermissionEnum.DIRECTORY_MANAGE)))
			.build());
	}

	/** Staff member who may read the directory but not change it. */
	private void givenReadOnlyStaff(UUID communityId) {
		UserContextHolder.set(UserContext.builder()
			.userId(staffUserId.toString())
			.communityId(communityId.toString())
			.roles(List.of(SilinganRealmRole.STAFF))
			.communityAccess(new CommunityAccess(communityId, List.of(PermissionEnum.DIRECTORY_VIEW)))
			.build());
	}

	private void givenCommunityExists(UUID communityId) {
		when(communityRepository.findByIdAndStatus(communityId, CommunityStatus.ACTIVE))
			.thenReturn(Optional.of(Community.builder().id(communityId).build()));
	}

	private DirectoryContact givenContact(UUID communityId, boolean active) {
		DirectoryContact contact = DirectoryContact.builder()
			.id(UUID.randomUUID())
			.community(Community.builder().id(communityId).build())
			.communityId(communityId)
			.category(DirectoryContactCategory.COMMUNITY_SERVICE)
			.contactType(DirectoryContactType.PMO)
			.displayName("PMO Office")
			.primaryPhoneNumber("+63288881234")
			.displayOrder(1)
			.active(active)
			.build();
		when(directoryContactRepository.findByIdAndCommunityId(contact.getId(), communityId))
			.thenReturn(Optional.of(contact));
		return contact;
	}

	private CreateDirectoryContactRequest.CreateDirectoryContactRequestBuilder validCreateRequest(UUID communityId) {
		return CreateDirectoryContactRequest.builder()
			.communityId(communityId)
			.displayName("Barangay Emergency Hotline")
			.category(DirectoryContactCategory.EMERGENCY_SERVICE)
			.contactType(DirectoryContactType.BARANGAY_EMERGENCY_RESPONSE)
			.primaryPhoneNumber("+63288881234");
	}

	private DirectoryContact savedContact() {
		ArgumentCaptor<DirectoryContact> captor = ArgumentCaptor.forClass(DirectoryContact.class);
		verify(directoryContactRepository).save(captor.capture());
		return captor.getValue();
	}

	private void echoSave() {
		when(directoryContactRepository.save(any(DirectoryContact.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
	}

	// --- creation -----------------------------------------------------------------------------

	@Test
	void shouldCreateContactForManagedCommunity() {
		givenDirectoryManager(communityA);
		givenCommunityExists(communityA);
		echoSave();

		DirectoryContactResponse response = service.createContact(validCreateRequest(communityA).build());

		assertThat(response.communityId()).isEqualTo(communityA);
		assertThat(response.displayName()).isEqualTo("Barangay Emergency Hotline");
		assertThat(response.category()).isEqualTo(DirectoryContactCategory.EMERGENCY_SERVICE);
		assertThat(response.contactType()).isEqualTo(DirectoryContactType.BARANGAY_EMERGENCY_RESPONSE);
		assertThat(response.primaryPhoneNumber()).isEqualTo("+63288881234");
	}

	@Test
	void shouldAssignCreatedContactToExactlyOneCommunity() {
		givenDirectoryManager(communityA);
		givenCommunityExists(communityA);
		echoSave();

		service.createContact(validCreateRequest(communityA).build());

		DirectoryContact saved = savedContact();
		assertThat(saved.getCommunityId()).isEqualTo(communityA);
		assertThat(saved.getCommunity().getId()).isEqualTo(communityA);
	}

	@Test
	void shouldDefaultNewContactToActiveWithSafeDisplayOrder() {
		givenDirectoryManager(communityA);
		givenCommunityExists(communityA);
		echoSave();

		service.createContact(validCreateRequest(communityA).displayOrder(null).active(null).build());

		DirectoryContact saved = savedContact();
		assertThat(saved.isActive()).isTrue();
		assertThat(saved.getDisplayOrder()).isEqualTo(DirectoryContact.DEFAULT_DISPLAY_ORDER);
	}

	@Test
	void shouldAllowCreatingAnInactiveContactWhenExplicitlyRequested() {
		givenDirectoryManager(communityA);
		givenCommunityExists(communityA);
		echoSave();

		service.createContact(validCreateRequest(communityA).active(false).build());

		assertThat(savedContact().isActive()).isFalse();
	}

	@Test
	void shouldLeaveAuditFieldsToTheServerOnCreate() {
		givenDirectoryManager(communityA);
		givenCommunityExists(communityA);
		echoSave();

		service.createContact(validCreateRequest(communityA).build());

		// Auditing is applied by the persistence listener, never by the request payload.
		DirectoryContact saved = savedContact();
		assertThat(saved.getCreatedBy()).isNull();
		assertThat(saved.getUpdatedBy()).isNull();
		assertThat(saved.getCreatedAt()).isNull();
		assertThat(saved.getDeleted()).isFalse();
	}

	@Test
	void shouldRejectBlankDisplayNameOnCreate() {
		givenDirectoryManager(communityA);
		givenCommunityExists(communityA);

		assertThatThrownBy(() -> service.createContact(validCreateRequest(communityA).displayName("  ").build()))
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("Display name");
		verify(directoryContactRepository, never()).save(any());
	}

	@Test
	void shouldRejectBlankPrimaryPhoneNumberOnCreate() {
		givenDirectoryManager(communityA);
		givenCommunityExists(communityA);

		assertThatThrownBy(() -> service.createContact(validCreateRequest(communityA).primaryPhoneNumber(" ").build()))
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("Primary phone number");
		verify(directoryContactRepository, never()).save(any());
	}

	@Test
	void shouldRejectNegativeDisplayOrderOnCreate() {
		givenDirectoryManager(communityA);
		givenCommunityExists(communityA);

		assertThatThrownBy(() -> service.createContact(validCreateRequest(communityA).displayOrder(-1).build()))
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("Display order");
		verify(directoryContactRepository, never()).save(any());
	}

	@Test
	void shouldRejectCreateForUnknownCommunity() {
		givenDirectoryManager(communityA);
		when(communityRepository.findByIdAndStatus(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.createContact(validCreateRequest(communityA).build()))
			.isInstanceOf(NotFoundException.class);
		verify(directoryContactRepository, never()).save(any());
	}

	@Test
	void shouldRejectCreateWithoutCommunityId() {
		givenDirectoryManager(communityA);

		assertThatThrownBy(() -> service.createContact(validCreateRequest(null).build()))
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("Community ID");
	}

	@Test
	void shouldRejectCreateByStaffWithoutManagePermission() {
		givenReadOnlyStaff(communityA);
		givenCommunityExists(communityA);

		assertThatThrownBy(() -> service.createContact(validCreateRequest(communityA).build()))
			.isInstanceOf(ForbiddenException.class);
		verify(directoryContactRepository, never()).save(any());
	}

	@Test
	void shouldRejectCreateForAnotherCommunity() {
		givenDirectoryManager(communityA);
		givenCommunityExists(communityB);
		// The scope guard is the authority on membership and rejects the foreign community.
		Mockito.doThrow(new ForbiddenException("You do not have access to this community"))
			.when(communityScopeGuard).assertAccess(communityB);

		assertThatThrownBy(() -> service.createContact(validCreateRequest(communityB).build()))
			.isInstanceOf(ForbiddenException.class);
		verify(directoryContactRepository, never()).save(any());
	}

	@Test
	void shouldRejectCreateForUnauthenticatedCaller() {
		UserContextHolder.clear();

		assertThatThrownBy(() -> service.createContact(validCreateRequest(communityA).build()))
			.isInstanceOf(ForbiddenException.class);
		verify(directoryContactRepository, never()).save(any());
	}

	// --- staff retrieval ----------------------------------------------------------------------

	@Test
	void shouldListContactsOfTheCallersCommunity() {
		givenDirectoryManager(communityA);
		DirectoryContact active = givenContact(communityA, true);
		DirectoryContact inactive = givenContact(communityA, false);
		when(directoryContactRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(active, inactive)));

		List<DirectoryContactResponse> contacts =
			service.getContacts(null, null, PageRequest.of(0, 20)).getContent();

		// Staff see both states; only residents are restricted to active contacts.
		assertThat(contacts).extracting(DirectoryContactResponse::active).containsExactly(true, false);
	}

	@Test
	void shouldScopeStaffListingToTheRequestedCommunityOnlyAfterMembershipCheck() {
		givenDirectoryManager(communityA);
		Mockito.doThrow(new ForbiddenException("You do not have access to this community"))
			.when(communityScopeGuard).assertAccess(communityB);

		assertThatThrownBy(() -> service.getContacts(communityB, null, PageRequest.of(0, 20)))
			.isInstanceOf(ForbiddenException.class);
		verify(directoryContactRepository, never()).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	void shouldIgnoreClientSuppliedSortAndCapPageSize() {
		givenDirectoryManager(communityA);
		when(directoryContactRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of()));

		service.getContacts(null, null, PageRequest.of(2, 5000, Sort.by("createdBy")));

		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		verify(directoryContactRepository).findAll(any(Specification.class), captor.capture());
		// Arbitrary property names must never reach the database as a sort expression.
		assertThat(captor.getValue().getSort().isSorted()).isFalse();
		assertThat(captor.getValue().getPageSize()).isEqualTo(100);
		assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
	}

	@Test
	void shouldApplyFiltersWithinTheCallersCommunity() {
		givenDirectoryManager(communityA);
		when(directoryContactRepository.findAll(any(Specification.class), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of()));

		DirectoryContactFilterCriteria criteria = DirectoryContactFilterCriteria.builder()
			.category(DirectoryContactCategory.EMERGENCY_SERVICE)
			.contactType(DirectoryContactType.POLICE)
			.active(true)
			.keyword("police")
			.build();

		service.getContacts(null, criteria, PageRequest.of(0, 20));

		verify(communityScopeGuard).assertAccess(communityA);
		verify(directoryContactRepository).findAll(any(Specification.class), any(Pageable.class));
	}

	@Test
	void shouldReturnContactDetailFromTheCallersCommunity() {
		givenDirectoryManager(communityA);
		DirectoryContact contact = givenContact(communityA, true);

		DirectoryContactResponse response = service.getContactById(contact.getId());

		assertThat(response.id()).isEqualTo(contact.getId());
		assertThat(response.communityId()).isEqualTo(communityA);
	}

	@Test
	void shouldNotReturnAnotherCommunitysContact() {
		givenDirectoryManager(communityA);
		UUID foreignContactId = UUID.randomUUID();
		// Scoping the lookup by community means a guessed identifier simply finds nothing.
		when(directoryContactRepository.findByIdAndCommunityId(foreignContactId, communityA))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getContactById(foreignContactId))
			.isInstanceOf(NotFoundException.class)
			.hasMessage("Directory contact not found");
	}

	@Test
	void shouldReturnNotFoundForMissingContact() {
		givenDirectoryManager(communityA);
		when(directoryContactRepository.findByIdAndCommunityId(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getContactById(UUID.randomUUID()))
			.isInstanceOf(NotFoundException.class);
	}

	// --- update -------------------------------------------------------------------------------

	private UpdateDirectoryContactRequest.UpdateDirectoryContactRequestBuilder validUpdateRequest() {
		return UpdateDirectoryContactRequest.builder()
			.displayName("Updated PMO Office")
			.category(DirectoryContactCategory.UTILITY_PROVIDER)
			.contactType(DirectoryContactType.WATER_PROVIDER)
			.primaryPhoneNumber("1627")
			.secondaryPhoneNumber("+63 (2) 8888-1234")
			.description("Water service hotline")
			.operatingHours("Mon-Fri 8AM-5PM")
			.address("Main Street")
			.displayOrder(5);
	}

	@Test
	void shouldUpdateEditableFieldsOfOwnCommunityContact() {
		givenDirectoryManager(communityA);
		DirectoryContact contact = givenContact(communityA, true);
		echoSave();

		DirectoryContactResponse response = service.updateContact(contact.getId(), validUpdateRequest().build());

		assertThat(response.displayName()).isEqualTo("Updated PMO Office");
		assertThat(response.category()).isEqualTo(DirectoryContactCategory.UTILITY_PROVIDER);
		assertThat(response.contactType()).isEqualTo(DirectoryContactType.WATER_PROVIDER);
		assertThat(response.primaryPhoneNumber()).isEqualTo("1627");
		assertThat(response.secondaryPhoneNumber()).isEqualTo("+63 (2) 8888-1234");
		assertThat(response.description()).isEqualTo("Water service hotline");
		assertThat(response.operatingHours()).isEqualTo("Mon-Fri 8AM-5PM");
		assertThat(response.address()).isEqualTo("Main Street");
		assertThat(response.displayOrder()).isEqualTo(5);
	}

	@Test
	void shouldNotChangeCommunityOwnershipOnUpdate() {
		givenDirectoryManager(communityA);
		DirectoryContact contact = givenContact(communityA, true);
		echoSave();

		service.updateContact(contact.getId(), validUpdateRequest().build());

		DirectoryContact saved = savedContact();
		assertThat(saved.getCommunityId()).isEqualTo(communityA);
		assertThat(saved.getCommunity().getId()).isEqualTo(communityA);
	}

	@Test
	void shouldNotChangeActiveStateThroughUpdate() {
		givenDirectoryManager(communityA);
		DirectoryContact contact = givenContact(communityA, false);
		echoSave();

		DirectoryContactResponse response = service.updateContact(contact.getId(), validUpdateRequest().build());

		// Visibility is owned by the activate/deactivate endpoints.
		assertThat(response.active()).isFalse();
	}

	@Test
	void shouldRejectUpdateWithInvalidValues() {
		givenDirectoryManager(communityA);
		DirectoryContact contact = givenContact(communityA, true);

		assertThatThrownBy(() -> service.updateContact(contact.getId(), validUpdateRequest().displayName("").build()))
			.isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> service.updateContact(contact.getId(), validUpdateRequest().displayOrder(-5).build()))
			.isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> service.updateContact(contact.getId(), validUpdateRequest().category(null).build()))
			.isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> service.updateContact(contact.getId(), validUpdateRequest().contactType(null).build()))
			.isInstanceOf(ValidationException.class);
		verify(directoryContactRepository, never()).save(any());
	}

	@Test
	void shouldRejectUpdateOfAnotherCommunitysContact() {
		givenDirectoryManager(communityA);
		UUID foreignContactId = UUID.randomUUID();
		when(directoryContactRepository.findByIdAndCommunityId(foreignContactId, communityA))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.updateContact(foreignContactId, validUpdateRequest().build()))
			.isInstanceOf(NotFoundException.class);
		verify(directoryContactRepository, never()).save(any());
	}

	@Test
	void shouldRejectUpdateByStaffWithoutManagePermission() {
		givenReadOnlyStaff(communityA);
		DirectoryContact contact = givenContact(communityA, true);

		assertThatThrownBy(() -> service.updateContact(contact.getId(), validUpdateRequest().build()))
			.isInstanceOf(ForbiddenException.class);
		verify(directoryContactRepository, never()).save(any());
	}

	// --- activation ---------------------------------------------------------------------------

	@Test
	void shouldDeactivateAndReactivateContact() {
		givenDirectoryManager(communityA);
		DirectoryContact contact = givenContact(communityA, true);
		echoSave();

		assertThat(service.deactivateContact(contact.getId()).active()).isFalse();
		assertThat(service.activateContact(contact.getId()).active()).isTrue();
	}

	@Test
	void shouldNotWriteWhenActiveStateIsUnchanged() {
		givenDirectoryManager(communityA);
		DirectoryContact contact = givenContact(communityA, true);

		assertThat(service.activateContact(contact.getId()).active()).isTrue();

		verify(directoryContactRepository, never()).save(any());
	}

	@Test
	void shouldRejectActivationOfAnotherCommunitysContact() {
		givenDirectoryManager(communityA);
		UUID foreignContactId = UUID.randomUUID();
		when(directoryContactRepository.findByIdAndCommunityId(foreignContactId, communityA))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.activateContact(foreignContactId)).isInstanceOf(NotFoundException.class);
		assertThatThrownBy(() -> service.deactivateContact(foreignContactId)).isInstanceOf(NotFoundException.class);
		verify(directoryContactRepository, never()).save(any());
	}

	@Test
	void shouldRejectActivationByStaffWithoutManagePermission() {
		givenReadOnlyStaff(communityA);
		DirectoryContact contact = givenContact(communityA, false);

		assertThatThrownBy(() -> service.activateContact(contact.getId())).isInstanceOf(ForbiddenException.class);
		verify(directoryContactRepository, never()).save(any());
	}

	// --- deletion -----------------------------------------------------------------------------

	@Test
	void shouldSoftDeleteContactOfOwnCommunity() {
		givenDirectoryManager(communityA);
		DirectoryContact contact = givenContact(communityA, true);
		echoSave();

		service.deleteContact(contact.getId());

		DirectoryContact saved = savedContact();
		assertThat(saved.getDeleted()).isTrue();
		// History is preserved rather than removed, matching the project's soft-delete convention.
		assertThat(saved.getId()).isEqualTo(contact.getId());
		assertThat(saved.getDisplayName()).isEqualTo("PMO Office");
	}

	@Test
	void shouldReturnNotFoundWhenDeletingAnAlreadyDeletedOrMissingContact() {
		givenDirectoryManager(communityA);
		when(directoryContactRepository.findByIdAndCommunityId(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.deleteContact(UUID.randomUUID()))
			.isInstanceOf(NotFoundException.class);
		verify(directoryContactRepository, never()).save(any());
	}

	@Test
	void shouldRejectDeletionOfAnotherCommunitysContact() {
		givenDirectoryManager(communityA);
		UUID foreignContactId = UUID.randomUUID();
		when(directoryContactRepository.findByIdAndCommunityId(foreignContactId, communityA))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.deleteContact(foreignContactId)).isInstanceOf(NotFoundException.class);
		verify(directoryContactRepository, never()).save(any());
	}

	@Test
	void shouldRejectDeletionByStaffWithoutManagePermission() {
		givenReadOnlyStaff(communityA);
		DirectoryContact contact = givenContact(communityA, true);

		assertThatThrownBy(() -> service.deleteContact(contact.getId())).isInstanceOf(ForbiddenException.class);
		// A rejected delete leaves the contact untouched.
		assertThat(contact.getDeleted()).isFalse();
		verify(directoryContactRepository, never()).save(any());
	}

	// --- scope resolution ---------------------------------------------------------------------

	@Test
	void shouldRejectStaffOperationsWithoutResolvableCommunityScope() {
		UserContextHolder.set(UserContext.builder()
			.userId(staffUserId.toString())
			.communityId(null)
			.roles(List.of(SilinganRealmRole.STAFF))
			.build());

		assertThatThrownBy(() -> service.getContactById(UUID.randomUUID())).isInstanceOf(ForbiddenException.class);
		assertThatThrownBy(() -> service.deleteContact(UUID.randomUUID())).isInstanceOf(ForbiddenException.class);
	}

	@Test
	void shouldRejectContactIdThatIsNotSupplied() {
		givenDirectoryManager(communityA);

		assertThatThrownBy(() -> service.getContactById(null))
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("Contact ID");
	}

	@Test
	void staffResponseShouldNotExposeSoftDeleteState() {
		List<String> components = java.util.Arrays.stream(DirectoryContactResponse.class.getRecordComponents())
			.map(java.lang.reflect.RecordComponent::getName)
			.toList();

		assertThat(components).doesNotContain("deleted", "community");
		assertThat(components).contains("createdAt", "updatedAt", "createdBy", "updatedBy", "active");
	}

	@Test
	void unusedUserMappingShouldTolerateMissingAuditUser() {
		// The mapper flattens the audit relation to an identifier; a null relation must not fail.
		assertThat(new DirectoryContactMapperImpl().map((User) null)).isNull();
	}
}
