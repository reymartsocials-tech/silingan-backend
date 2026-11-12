package com.ria.olita.tech.silingan.service.qr;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.dto.res.CommunityJoinQrImage;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinQrMetadataResponse;
import com.ria.olita.tech.silingan.entity.Community;
import com.ria.olita.tech.silingan.entity.CommunityStatus;
import com.ria.olita.tech.silingan.exception.ConflictException;
import com.ria.olita.tech.silingan.exception.NotFoundException;
import com.ria.olita.tech.silingan.exception.ValidationException;
import com.ria.olita.tech.silingan.repository.CommunityRepository;
import com.ria.olita.tech.silingan.security.scope.CommunityScopeGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the authoritative community code of a community and renders it as a join QR code.
 *
 * <p>The QR is a convenience wrapper around the existing community code: it encodes the configured
 * SILINGAN join URL so a scanning resident lands on the same community-code onboarding journey used
 * by manual entry. Nothing is persisted; the image is rendered per request and always reflects the
 * community code currently stored on the community.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityJoinQrServiceImpl implements CommunityJoinQrService {

	static final String QR_CODE_URL_TEMPLATE = "/api/v1/communities/%s/join-qr";

	private static final Pattern NON_FILENAME_CHARS = Pattern.compile("[^a-z0-9]+");
	private static final int FILENAME_NAME_MAX = 40;

	private final CommunityRepository communityRepository;
	private final CommunityScopeGuard communityScopeGuard;
	private final CommunityJoinUrlFactory communityJoinUrlFactory;
	private final QrCodeGenerator qrCodeGenerator;

	@Override
	@Transactional(readOnly = true)
	public CommunityJoinQrImage generateJoinQr(UUID communityId) {
		Community community = resolveEligibleCommunity(communityId);
		URI joinUrl = communityJoinUrlFactory.create(community.getCommunityCode());

		byte[] png = qrCodeGenerator.generatePng(joinUrl.toString());

		log.info("Community join QR generated for community {}", community.getId());

		return CommunityJoinQrImage.builder()
			.fileName(toFileName(community.getName()))
			.content(png)
			.build();
	}

	@Override
	@Transactional(readOnly = true)
	public CommunityJoinQrMetadataResponse getJoinQrMetadata(UUID communityId) {
		Community community = resolveEligibleCommunity(communityId);
		URI joinUrl = communityJoinUrlFactory.create(community.getCommunityCode());

		return CommunityJoinQrMetadataResponse.builder()
			.communityId(community.getId())
			.communityName(community.getName())
			.communityCode(community.getCommunityCode())
			.joinUrl(joinUrl.toString())
			.qrCodeUrl(QR_CODE_URL_TEMPLATE.formatted(community.getId()))
			.build();
	}

	/**
	 * Resolves the community the caller is authorized to manage. The community ID is re-checked
	 * against the caller's actual memberships so a Community Admin of one community cannot read
	 * another community's join material by changing the path variable.
	 */
	private Community resolveEligibleCommunity(UUID communityId) {
		if (communityId == null) {
			throw new ValidationException("Community id is required");
		}

		communityScopeGuard.assertAccess(communityId);

		Community community = communityRepository.findById(communityId)
			.orElseThrow(() -> new NotFoundException("Community not found with id = " + communityId));

		if (community.getStatus() != CommunityStatus.ACTIVE) {
			throw new ConflictException("Community is not accepting new residents");
		}

		if (community.getCommunityCode() == null || community.getCommunityCode().isBlank()) {
			throw new ConflictException("Community does not have a join code");
		}

		return community;
	}

	private String toFileName(String communityName) {
		String slug = NON_FILENAME_CHARS
			.matcher(communityName == null ? "" : communityName.toLowerCase(Locale.ROOT))
			.replaceAll("-")
			.replaceAll("^-|-$", "");

		if (slug.isBlank()) {
			slug = "community";
		} else if (slug.length() > FILENAME_NAME_MAX) {
			slug = slug.substring(0, FILENAME_NAME_MAX)
				.replaceAll("-$", "");
		}

		return "silingan-%s-join.png".formatted(slug);
	}
}
