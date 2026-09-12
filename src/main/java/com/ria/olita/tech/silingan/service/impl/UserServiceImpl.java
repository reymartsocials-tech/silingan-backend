package com.ria.olita.tech.silingan.service.impl;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.ria.olita.tech.silingan.dto.req.CreateUserRequest;
import com.ria.olita.tech.silingan.dto.res.CommunityResponse;
import com.ria.olita.tech.silingan.dto.res.CreatedUserResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.exception.ConflictException;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.service.CommunityService;
import com.ria.olita.tech.silingan.service.KeycloakService;
import com.ria.olita.tech.silingan.service.UserService;
import com.ria.olita.tech.silingan.service.otp.RegistrationOtpProofService;
import com.ria.olita.tech.silingan.util.ContactNormalizer;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {
	private final KeycloakService keycloakService;
	private final UserRepository userRepository;
	private final CommunityRepository communityRepository;
	private final RegistrationOtpProofService registrationOtpProofService;
	private final CommunityService communityService;

	@Override
	public void createUser(CreateUserRequest request) {
		createUserInternal(request);
	}

	@Override
	public CreatedUserResponse createSelfServiceUser(CreateUserRequest request) {
		boolean hasOtpProof = registrationOtpProofService.consumeRegistrationProof(request.mobileNumber());
		if (!hasOtpProof) {
			throw new ForbiddenException("Mobile number must be OTP verified before self-service registration");
		}

		return createUserInternal(request.withCommunityRole(SilinganRealmRole.RESIDENT));
	}

	private CreatedUserResponse createUserInternal(CreateUserRequest request) {
		log.info("Registering user in kc and in db: {}", request.username());

		String email = ContactNormalizer.normalizeEmail(request.email());
		String mobileNumber = ContactNormalizer.normalizeMobileNumber(request.mobileNumber());

		// Checked before the Keycloak call so a rejected duplicate does not leave an orphaned
		// Keycloak account behind.
		assertContactDetailsAreUnique(email, mobileNumber);

		CommunityResponse community = communityService.getByCode(request.communityCode());
		String keycloakUserId = keycloakService.createUser(request, community.id());
		log.info("User saved to kc with ID: {}", keycloakUserId);
		Community communityEntity = communityRepository.findById(community.id()).get();

		User user = User.builder()
			.keycloakUserId(keycloakUserId)
			.username(request.username())
			.email(email)
			.mobileNumber(mobileNumber)
			.firstName(request.firstName())
			.lastName(request.lastName())
			.build();

		UserCommunity userCommunity = UserCommunity.builder()
			.user(user)
			.community(communityEntity)
			.role(request.communityRole())
			.build();

		user.addCommunity(userCommunity);

		try {
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException ex) {
			// Race-safe backstop: two concurrent registrations both pass the pre-check above and
			// only the database constraint can arbitrate.
			log.warn("Duplicate user rejected by database constraint for username={}", request.username());
			throw new ConflictException("Email or mobile number is already registered");
		}
		log.info("User saved to database with ID: {}", user.getId());

		return CreatedUserResponse.builder()
			.id(user.getId())
			.keycloakUserId(user.getKeycloakUserId())
			.username(user.getUsername())
			.email(user.getEmail())
			.mobileNumber(user.getMobileNumber())
			.communityId(community.id())
			.communityCode(community.communityCode())
			.communityRole(request.communityRole())
			.build();
	}

	private void assertContactDetailsAreUnique(String email, String mobileNumber) {
		if (email != null && userRepository.existsByEmailAndDeletedFalse(email)) {
			throw new ConflictException("Email is already registered");
		}
		if (mobileNumber != null && userRepository.existsByMobileNumberAndDeletedFalse(mobileNumber)) {
			throw new ConflictException("Mobile number is already registered");
		}
	}
}
