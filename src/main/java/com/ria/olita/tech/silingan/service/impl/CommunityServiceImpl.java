package com.ria.olita.tech.silingan.service.impl;

import com.ria.olita.tech.silingan.dto.req.CreateCommunityRequest;
import com.ria.olita.tech.silingan.dto.req.UpdateCommunityRequest;
import com.ria.olita.tech.silingan.dto.res.CommunityCodeResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityResponse;
import com.ria.olita.tech.silingan.entity.Address;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.entity.CommunityType;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.exception.ConflictException;
import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.mapper.AddressMapper;
import com.ria.olita.tech.silingan.mapper.CommunityMapper;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.repository.TenantRepository;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.repository.UserRepository;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.service.CommunityCodeService;
import com.ria.olita.tech.silingan.service.CommunityService;
import com.ria.olita.tech.silingan.service.KeycloakService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class CommunityServiceImpl implements CommunityService {

	private final CommunityRepository communityRepository;
	private static final List<String> ADMIN_REQUIRED_ACTIONS = List.of("VERIFY_EMAIL", "UPDATE_PROFILE", "UPDATE_PASSWORD");

	@Value("${app.invitations.expiry-days:7}")
	private int invitationExpiryDays;

	private final TenantRepository tenantRepository;
	private final UserRepository userRepository;
	private final UserCommunityRepository userCommunityRepository;
	private final CommunityMapper communityMapper;
	private final AddressMapper addressMapper;
	private final KeycloakService keycloakService;
	private final CommunityCodeService communityCodeService;


	@Override
	public CommunityResponse create(CreateCommunityRequest request) {
		CommunityCodeResponse codeResponse = communityCodeService.generateCodes(request.type(), request.address());
		Community community = communityMapper.toEntity(request);
		community.setStatus(CommunityStatus.DRAFT);
		community.setCommunityCode(codeResponse.displayCode());
		community.setSystemGenCode(codeResponse.systemCode());

		// Handle address
		if (request.address() != null) {
			Address address = addressMapper.toEntity(request.address());
			community.setAddress(address);
		}

		var tenant = tenantRepository.findById(request.tenantId())
			.orElseThrow(() -> new NotFoundException("Tenant not found with id =" + request.tenantId()));
		community.setTenant(tenant);

		Community createdCommunity = communityRepository.save(community);
		return communityMapper.toResponse(createdCommunity);
	}

	@Override
	@Transactional(readOnly = true)
	public CommunityResponse getById(UUID id) {
		Community community = communityRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Community not found with id =" + id));
		return communityMapper.toResponse(community);
	}

	@Override
	@Transactional(readOnly = true)
	public CommunityResponse getByCode(String code) {
		Community community = communityRepository.findByCodeAndStatus(code, CommunityStatus.ACTIVE)
			.orElseThrow(() -> new NotFoundException("Community code not found with code =" + code));
		return communityMapper.toResponse(community);
	}

	@Override
	@Transactional(readOnly = true)
	public List<CommunityResponse> getAll() {
		return communityRepository.findAll()
			.stream()
			.map(communityMapper::toResponse)
			.collect(Collectors.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public List<CommunityResponse> getByStatus(CommunityStatus status) {
		return communityRepository.findByStatus(status)
			.stream()
			.map(communityMapper::toResponse)
			.collect(Collectors.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public List<CommunityResponse> getByType(CommunityType type) {
		return communityRepository.findByType(type)
			.stream()
			.map(communityMapper::toResponse)
			.collect(Collectors.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public List<CommunityResponse> getByUserId(UUID userId) {
		var user = userRepository.findById(userId)
			.orElseThrow(() -> new NotFoundException("User not found with id = " + userId));
		return user.getJoinedCommunities()
			.stream()
			.map(communityMapper::toResponse)
			.collect(Collectors.toList());
	}

	@Override
	public CommunityResponse update(UUID id, UpdateCommunityRequest request) {
		Community community = communityRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Community not found with id = " + id));

		communityMapper.updateEntityFromRequest(request, community);

		// Handle address update
		if (request.address() != null) {
			if (community.getAddress() != null) {
				addressMapper.updateEntityFromRequest(request.address(), community.getAddress());
			} else {
				Address address = addressMapper.toEntity(request.address());
				community.setAddress(address);
			}
		}

		if (community.getTenant() == null) {
			throw new ValidationException("Community tenant is required");
		}

		Community updated = communityRepository.save(community);
		return communityMapper.toResponse(updated);
	}

	@Override
	public void updateStatus(UUID id, CommunityStatus status) {
		Community community = communityRepository.findById(id)
			.orElseThrow(() -> new NotFoundException("Community not found with id = " + id));

		if (community.getStatus() == CommunityStatus.ARCHIVE) {
			throw new ConflictException("Cannot change status of archived community");
		}

		if (status == CommunityStatus.ACTIVE && community.getStatus() != CommunityStatus.ACTIVE) {
			validateCommunityReadinessForActivation(community);
		}

		if (status != null && community.getStatus() != status) {
			community.setStatus(status);
		}
		communityRepository.save(community);
	}

	private void validateCommunityReadinessForActivation(Community community) {
		if (!StringUtils.hasText(community.getName())) {
			throw new ValidationException("Cannot activate community: community name is required");
		}

		if (!StringUtils.hasText(community.getCommunityCode())) {
			throw new ValidationException("Cannot activate community: community code is required");
		}

		if (!userCommunityRepository.hasRoleInCommunity(community.getId(), SilinganRealmRole.COMMUNITY_ADMIN)) {
			throw new ValidationException("Cannot activate community: community administrator is required");
		}
	}

	@Override
	public void delete(UUID id) {
		if (!communityRepository.existsById(id)) {
			throw new NotFoundException("Community not found with id = " + id);
		}
		communityRepository.deleteById(id);
	}


	@Override
	public void switchCommunity(UUID communityId) {
		if (!UserContextHolder.isPlatformAdmin()) {
			String currentUserId = UserContextHolder.get() != null ? UserContextHolder.get().userId() : null;
			if (currentUserId == null) {
				throw new ForbiddenException("No authenticated user context");
			}

			UUID userId;
			try {
				userId = UUID.fromString(currentUserId);
			} catch (IllegalArgumentException ex) {
				throw new ForbiddenException("Invalid authenticated user context");
			}

			if (userCommunityRepository.findByUserIdAndCommunityIdAndUserStatusActive(userId, communityId).isEmpty()) {
				throw new ForbiddenException("You do not have access to this community");
			}
		}

		keycloakService.updateUserAttributes(
			UserContextHolder.get()
				.keycloakUserId(),
			Map.of("communityId", List.of(communityId.toString()))
		);
	}

	@Override
	@Transactional(readOnly = true)
	public List<CommunityResponse> searchByCommunityNameOrCode(String searchTerm) {
		if (!StringUtils.hasText(searchTerm)) {
			throw new ValidationException("Search term cannot be empty");
		}

		String trimmedSearchTerm = searchTerm.trim();
		return communityRepository.searchByCommunityNameOrCodeAndStatus(trimmedSearchTerm, trimmedSearchTerm, CommunityStatus.ACTIVE)
			.stream()
			.map(communityMapper::toResponse)
			.collect(Collectors.toList());
	}
}
