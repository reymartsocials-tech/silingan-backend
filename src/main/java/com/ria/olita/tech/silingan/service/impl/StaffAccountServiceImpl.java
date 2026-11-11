package com.ria.olita.tech.silingan.service.impl;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.req.CreateStaffAccountRequest;
import com.ria.olita.tech.silingan.dto.req.CreateUserRequest;
import com.ria.olita.tech.silingan.dto.req.EditStaffInfoRequest;
import com.ria.olita.tech.silingan.dto.res.StaffAccountResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.User;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.entity.UserStatus;
import com.ria.olita.tech.silingan.exception.ConflictException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.service.CommunityService;
import com.ria.olita.tech.silingan.service.KeycloakService;
import com.ria.olita.tech.silingan.service.StaffAccountService;
import com.ria.olita.tech.silingan.util.ContactNormalizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class StaffAccountServiceImpl implements StaffAccountService {

	private final UserRepository userRepository;
	private final CommunityRepository communityRepository;
	private final UserCommunityRepository userCommunityRepository;
	private final KeycloakService keycloakService;
	private final CommunityService communityService;

	@Override
	public StaffAccountResponse createStaffAccount(UUID communityId, CreateStaffAccountRequest request) {
		if (!communityRepository.existsById(communityId)) {
			throw new NotFoundException("Community not found");
		}

		String email = ContactNormalizer.normalizeEmail(request.email());
		String mobileNumber = ContactNormalizer.normalizeMobileNumber(request.mobileNumber());

		assertContactDetailsAreUnique(email, mobileNumber);

		Community community = communityRepository.findById(communityId)
			.orElseThrow(() -> new NotFoundException("Community not found"));

		CreateUserRequest userRequest = CreateUserRequest.builder()
			.username(mobileNumber)
			.email(email)
			.firstName(request.firstName())
			.lastName(request.lastName())
			.mobileNumber(mobileNumber)
			.password(generateTemporaryPassword())
			.enabled(true)
			.emailVerified(false)
			.communityRole(SilinganRealmRole.STAFF)
			.communityCode(community.getCommunityCode())
			.build();

		String keycloakUserId = keycloakService.createUser(userRequest, communityId);
		log.info("Staff account created in Keycloak with ID: {}", keycloakUserId);

		User user = User.builder()
			.keycloakUserId(keycloakUserId)
			.username(userRequest.username())
			.email(email)
			.mobileNumber(mobileNumber)
			.firstName(request.firstName())
			.lastName(request.lastName())
			.status(UserStatus.ACTIVE)
			.build();

		UserCommunity userCommunity = UserCommunity.builder()
			.user(user)
			.community(community)
			.role(SilinganRealmRole.STAFF)
			.roleCode(request.staffRole())
			.build();

		user.addCommunity(userCommunity);
		user.setLastSelectedCommunity(community);

		try {
			userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException ex) {
			log.warn("Duplicate staff account rejected: {}", request.email());
			throw new ConflictException("Email or mobile number is already registered");
		}

		log.info("Staff account created for user {} in community {}", user.getId(), communityId);

		return toStaffAccountResponse(user, userCommunity);
	}

	@Override
	public StaffAccountResponse editStaffInfo(UUID communityId, UUID staffUserId, EditStaffInfoRequest request) {
		User user = userRepository.findById(staffUserId)
			.orElseThrow(() -> new NotFoundException("Staff user not found"));

		UserCommunity userCommunity = userCommunityRepository.findByUserIdAndCommunityId(staffUserId, communityId)
			.orElseThrow(() -> new NotFoundException("Staff member not found in this community"));

		String normalizedMobileNumber = ContactNormalizer.normalizeMobileNumber(request.mobileNumber());

		if (!normalizedMobileNumber.equals(user.getMobileNumber()) 
			&& userRepository.existsByMobileNumberAndDeletedFalse(normalizedMobileNumber)) {
			throw new ConflictException("Mobile number is already in use");
		}

		user.setFirstName(request.firstName());
		user.setLastName(request.lastName());
		user.setMobileNumber(normalizedMobileNumber);

		userRepository.saveAndFlush(user);
		
		java.util.Map<String, java.util.List<String>> attributes = new java.util.HashMap<>();
		attributes.put("firstName", java.util.List.of(request.firstName()));
		attributes.put("lastName", java.util.List.of(request.lastName()));
		attributes.put("mobileNumber", java.util.List.of(normalizedMobileNumber));
		
		keycloakService.updateUserAttributes(user.getKeycloakUserId(), attributes);
		log.info("Staff info updated for user {} in community {} by admin action", staffUserId, communityId);

		return toStaffAccountResponse(user, userCommunity);
	}

	@Override
	public StaffAccountResponse deactivateStaff(UUID communityId, UUID staffUserId) {
		User user = userRepository.findById(staffUserId)
			.orElseThrow(() -> new NotFoundException("Staff user not found"));

		UserCommunity userCommunity = userCommunityRepository.findByUserIdAndCommunityId(staffUserId, communityId)
			.orElseThrow(() -> new NotFoundException("Staff member not found in this community"));

		if (isLastCommunityAdmin(communityId, staffUserId)) {
			throw new IllegalStateException("Cannot deactivate the final Community Admin. Assign another admin first.");
		}

		user.setStatus(UserStatus.INACTIVE);
		userRepository.saveAndFlush(user);

		log.info("Staff account deactivated for user {} in community {}", staffUserId, communityId);

		return toStaffAccountResponse(user, userCommunity);
	}

	@Override
	public StaffAccountResponse activateStaff(UUID communityId, UUID staffUserId) {
		User user = userRepository.findById(staffUserId)
			.orElseThrow(() -> new NotFoundException("Staff user not found"));

		UserCommunity userCommunity = userCommunityRepository.findByUserIdAndCommunityId(staffUserId, communityId)
			.orElseThrow(() -> new NotFoundException("Staff member not found in this community"));

		user.setStatus(UserStatus.ACTIVE);
		userRepository.saveAndFlush(user);

		log.info("Staff account activated for user {} in community {}", staffUserId, communityId);

		return toStaffAccountResponse(user, userCommunity);
	}

	private StaffAccountResponse toStaffAccountResponse(User user, UserCommunity userCommunity) {
		return new StaffAccountResponse(
			user.getId(),
			user.getFirstName(),
			user.getLastName(),
			user.getEmail(),
			user.getMobileNumber(),
			userCommunity.getRoleCode(),
			user.getStatus(),
			userCommunity.getUpdatedAt()
		);
	}

	private void assertContactDetailsAreUnique(String email, String mobileNumber) {
		if (email != null && userRepository.existsByEmailAndDeletedFalse(email)) {
			throw new ConflictException("Email is already registered");
		}
		if (mobileNumber != null && userRepository.existsByMobileNumberAndDeletedFalse(mobileNumber)) {
			throw new ConflictException("Mobile number is already registered");
		}
	}

	private String generateTemporaryPassword() {
		return "TempPass" + System.currentTimeMillis();
	}

	@Override
	public boolean isLastCommunityAdmin(UUID communityId, UUID userId) {
		int activeCommunityAdminCount = userCommunityRepository.countByCommunityIdAndRoleAndUserStatus(
			communityId,
			SilinganRealmRole.COMMUNITY_ADMIN,
			UserStatus.ACTIVE
		);
		
		if (activeCommunityAdminCount <= 1) {
			UserCommunity userCommunity = userCommunityRepository.findByUserIdAndCommunityId(userId, communityId)
				.orElse(null);
			
			if (userCommunity != null && userCommunity.getRole() == SilinganRealmRole.COMMUNITY_ADMIN) {
				return true;
			}
		}
		
		return false;
	}
}
