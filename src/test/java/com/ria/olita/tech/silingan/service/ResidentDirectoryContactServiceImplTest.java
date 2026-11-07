package com.ria.olita.tech.silingan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;

import com.ria.olita.tech.silingan.dto.res.ResidentDirectoryContactResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.DirectoryContact;
import com.ria.olita.tech.silingan.entity.DirectoryContactCategory;
import com.ria.olita.tech.silingan.entity.DirectoryContactType;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.mapper.DirectoryContactMapperImpl;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.DirectoryContactRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.rest.SecuredDirectoryContactController;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.security.scope.CommunityScopeGuard;
import com.ria.olita.tech.silingan.service.impl.DirectoryContactServiceImpl;

/**
 * Resident-facing directory retrieval: active-only visibility, community isolation and the
 * emergency-first ordering contract.
 */
class ResidentDirectoryContactServiceImplTest {

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

	@AfterEach
	void tearDown() {
		UserContextHolder.clear();
	}

	// --- fixtures -----------------------------------------------------------------------------

	private UUID givenResident(UUID activeCommunityId) {
		UUID userId = UUID.randomUUID();

		UserContextHolder.set(UserContext.builder()
			.userId(userId.toString())
			.communityId(activeCommunityId == null ? null : activeCommunityId.toString())
			.roles(List.of(SilinganRealmRole.RESIDENT))
			.build());

		if (activeCommunityId != null) {
			when(userCommunityRepository.findByUserIdAndCommunityIdAndUserStatusActive(userId, activeCommunityId))
				.thenReturn(Optional.of(UserCommunity.builder()
					.id(UUID.randomUUID())
					.user(User.builder().id(userId).build())
					.userId(userId)
					.communityId(activeCommunityId)
					.role(SilinganRealmRole.RESIDENT)
					.build()));
		}
		return userId;
	}

	/** A resident whose membership is no longer active: the repository returns nothing. */
	private void givenResidentWithInactiveMembership() {
		UUID userId = UUID.randomUUID();
		UserContextHolder.set(UserContext.builder()
			.userId(userId.toString())
			.communityId(communityA.toString())
			.roles(List.of(SilinganRealmRole.RESIDENT))
			.build());
		when(userCommunityRepository.findByUserIdAndCommunityIdAndUserStatusActive(userId, communityA))
			.thenReturn(Optional.empty());
	}

	private DirectoryContact contact(
		UUID communityId,
		DirectoryContactCategory category,
		DirectoryContactType type,
		String displayName,
		int displayOrder
	) {
		return DirectoryContact.builder()
			.id(UUID.randomUUID())
			.community(Community.builder().id(communityId).build())
			.communityId(communityId)
			.category(category)
			.contactType(type)
			.displayName(displayName)
			.primaryPhoneNumber("911")
			.secondaryPhoneNumber("+63 2 8888-1234")
			.description("Round the clock response")
			.operatingHours("24/7")
			.address("Main Street")
			.displayOrder(displayOrder)
			.active(true)
			.build();
	}

	// --- resident list ------------------------------------------------------------------------

	@Test
	void shouldReturnActiveContactsOfTheResidentsActiveCommunity() {
		givenResident(communityA);
		DirectoryContact pmo = contact(communityA, DirectoryContactCategory.COMMUNITY_SERVICE, DirectoryContactType.PMO, "PMO Office", 1);
		when(directoryContactRepository.findAll(any(Specification.class))).thenReturn(List.of(pmo));

		List<ResidentDirectoryContactResponse> contacts = service.getContactsForResident();

		assertThat(contacts).hasSize(1);
		assertThat(contacts.get(0).displayName()).isEqualTo("PMO Office");
		assertThat(contacts.get(0).primaryPhoneNumber()).isEqualTo("911");
		assertThat(contacts.get(0).operatingHours()).isEqualTo("24/7");
	}

	@Test
	void shouldReturnEmptyListForCommunityWithoutContacts() {
		givenResident(communityA);
		when(directoryContactRepository.findAll(any(Specification.class))).thenReturn(List.of());

		assertThat(service.getContactsForResident()).isEmpty();
	}

	@Test
	void shouldDelegateActiveAndCommunityFilteringToTheQuery() {
		givenResident(communityA);
		when(directoryContactRepository.findAll(any(Specification.class))).thenReturn(List.of());

		service.getContactsForResident();

		// The paged/in-memory overloads must not be used: filtering belongs in the query.
		verify(directoryContactRepository).findAll(any(Specification.class));
		verify(directoryContactRepository, never()).findAll(any(Specification.class), any(Pageable.class));
		verify(directoryContactRepository, never()).findAll();
	}

	@Test
	void shouldRejectResidentWithoutActiveMembership() {
		givenResidentWithInactiveMembership();

		assertThatThrownBy(() -> service.getContactsForResident())
			.isInstanceOf(ForbiddenException.class)
			.hasMessage("No active community membership");
		verify(directoryContactRepository, never()).findAll(any(Specification.class));
	}

	@Test
	void shouldRejectResidentWithoutActiveCommunity() {
		givenResident(null);

		assertThatThrownBy(() -> service.getContactsForResident())
			.isInstanceOf(ForbiddenException.class);
		verify(directoryContactRepository, never()).findAll(any(Specification.class));
	}

	@Test
	void shouldRejectUnauthenticatedResidentRequest() {
		UserContextHolder.clear();

		assertThatThrownBy(() -> service.getContactsForResident()).isInstanceOf(ForbiddenException.class);
		assertThatThrownBy(() -> service.getContactForResident(UUID.randomUUID()))
			.isInstanceOf(ForbiddenException.class);
		verify(directoryContactRepository, never()).findAll(any(Specification.class));
	}

	@Test
	void shouldScopeResidentListingToTheResolvedMembershipCommunity() {
		UUID userId = givenResident(communityA);
		when(directoryContactRepository.findAll(any(Specification.class))).thenReturn(List.of());

		service.getContactsForResident();

		// Membership is re-read from the database; the token community alone never grants access.
		verify(userCommunityRepository).findByUserIdAndCommunityIdAndUserStatusActive(userId, communityA);
		verify(userCommunityRepository, never()).findByUserIdAndCommunityIdAndUserStatusActive(userId, communityB);
	}

	// --- resident detail ----------------------------------------------------------------------

	@Test
	void shouldReturnActiveContactDetailForResident() {
		givenResident(communityA);
		DirectoryContact police = contact(communityA, DirectoryContactCategory.EMERGENCY_SERVICE, DirectoryContactType.POLICE, "Police", 0);
		when(directoryContactRepository.findByIdAndCommunityIdAndActiveTrue(police.getId(), communityA))
			.thenReturn(Optional.of(police));

		ResidentDirectoryContactResponse response = service.getContactForResident(police.getId());

		assertThat(response.id()).isEqualTo(police.getId());
		assertThat(response.category()).isEqualTo(DirectoryContactCategory.EMERGENCY_SERVICE);
		assertThat(response.contactType()).isEqualTo(DirectoryContactType.POLICE);
	}

	@Test
	void shouldHideInactiveDeletedAndForeignContactsBehindTheSameResponse() {
		givenResident(communityA);
		UUID inaccessibleContactId = UUID.randomUUID();
		// Inactive, soft-deleted and other-community contacts are all excluded by the query.
		when(directoryContactRepository.findByIdAndCommunityIdAndActiveTrue(inaccessibleContactId, communityA))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getContactForResident(inaccessibleContactId))
			.isInstanceOf(NotFoundException.class)
			.hasMessage("Directory contact not found");
	}

	@Test
	void shouldNotLetResidentReachAnotherCommunitysContactByChangingTheIdentifier() {
		givenResident(communityA);
		DirectoryContact foreign = contact(communityB, DirectoryContactCategory.EMERGENCY_SERVICE, DirectoryContactType.POLICE, "Police B", 0);
		when(directoryContactRepository.findByIdAndCommunityIdAndActiveTrue(foreign.getId(), communityB))
			.thenReturn(Optional.of(foreign));
		when(directoryContactRepository.findByIdAndCommunityIdAndActiveTrue(foreign.getId(), communityA))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getContactForResident(foreign.getId()))
			.isInstanceOf(NotFoundException.class);

		// The lookup was issued against the resident's own community, never the requested one.
		verify(directoryContactRepository).findByIdAndCommunityIdAndActiveTrue(foreign.getId(), communityA);
	}

	@Test
	void shouldRejectMissingContactIdentifier() {
		givenResident(communityA);

		assertThatThrownBy(() -> service.getContactForResident(null))
			.isInstanceOf(ValidationException.class)
			.hasMessageContaining("Contact ID");
	}

	@Test
	void residentResponseShouldNotExposeStaffOrAuditFields() {
		List<String> components = java.util.Arrays.stream(ResidentDirectoryContactResponse.class.getRecordComponents())
			.map(java.lang.reflect.RecordComponent::getName)
			.toList();

		assertThat(components).containsExactly(
			"id", "category", "contactType", "displayName", "primaryPhoneNumber",
			"secondaryPhoneNumber", "description", "operatingHours", "address");
		assertThat(components).doesNotContain(
			"communityId", "active", "deleted", "createdBy", "updatedBy", "createdAt", "updatedAt");
	}

	@Test
	void residentEndpointsShouldRequireAnAuthenticatedResident() {
		PreAuthorize preAuthorize = SecuredDirectoryContactController.class.getAnnotation(PreAuthorize.class);

		assertThat(preAuthorize).isNotNull();
		assertThat(preAuthorize.value()).contains("isAuthenticated()").contains("hasRole('RESIDENT')");
	}

	// --- ordering contract ----------------------------------------------------------------------

	@Test
	void emergencyServicesShouldOutrankEveryOtherCategory() {
		assertThat(DirectoryContactCategory.EMERGENCY_SERVICE.getDisplayPriority()).isZero();
		assertThat(DirectoryContactCategory.EMERGENCY_SERVICE.isEmergency()).isTrue();

		for (DirectoryContactCategory category : DirectoryContactCategory.values()) {
			if (category != DirectoryContactCategory.EMERGENCY_SERVICE) {
				assertThat(category.getDisplayPriority())
					.isGreaterThan(DirectoryContactCategory.EMERGENCY_SERVICE.getDisplayPriority());
				assertThat(category.isEmergency()).isFalse();
			}
		}
	}

	@Test
	void categoryPrioritiesShouldFollowTheAgreedDirectoryOrder() {
		List<DirectoryContactCategory> ordered = new ArrayList<>(List.of(DirectoryContactCategory.values()));
		ordered.sort(Comparator.comparingInt(DirectoryContactCategory::getDisplayPriority));

		assertThat(ordered).containsExactly(
			DirectoryContactCategory.EMERGENCY_SERVICE,
			DirectoryContactCategory.COMMUNITY_SERVICE,
			DirectoryContactCategory.UTILITY_PROVIDER,
			DirectoryContactCategory.OTHER_SERVICE);
	}

	@Test
	void categoryPrioritiesShouldBeUniqueSoOrderingIsDeterministic() {
		assertThat(List.of(DirectoryContactCategory.values()).stream()
			.map(DirectoryContactCategory::getDisplayPriority)
			.distinct()
			.count()).isEqualTo(DirectoryContactCategory.values().length);
	}

	@Test
	void serviceShouldPreserveTheOrderProducedByTheQuery() {
		givenResident(communityA);
		DirectoryContact police = contact(communityA, DirectoryContactCategory.EMERGENCY_SERVICE, DirectoryContactType.POLICE, "Police", 0);
		DirectoryContact fire = contact(communityA, DirectoryContactCategory.EMERGENCY_SERVICE, DirectoryContactType.FIRE_DEPARTMENT, "Fire Department", 1);
		DirectoryContact pmo = contact(communityA, DirectoryContactCategory.COMMUNITY_SERVICE, DirectoryContactType.PMO, "PMO", 0);
		DirectoryContact water = contact(communityA, DirectoryContactCategory.UTILITY_PROVIDER, DirectoryContactType.WATER_PROVIDER, "Water", 0);
		when(directoryContactRepository.findAll(any(Specification.class)))
			.thenReturn(List.of(police, fire, pmo, water));

		List<ResidentDirectoryContactResponse> contacts = service.getContactsForResident();

		// The service must not reshuffle what the ordered query returned.
		assertThat(contacts).extracting(ResidentDirectoryContactResponse::displayName)
			.containsExactly("Police", "Fire Department", "PMO", "Water");
	}
}
