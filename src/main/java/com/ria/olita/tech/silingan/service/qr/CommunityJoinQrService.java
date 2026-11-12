package com.ria.olita.tech.silingan.service.qr;

import java.util.UUID;

import com.ria.olita.tech.silingan.dto.res.CommunityJoinQrImage;
import com.ria.olita.tech.silingan.dto.res.CommunityJoinQrMetadataResponse;

/**
 * Produces the join QR code for a community so residents can reach the existing community-code
 * onboarding journey without typing the code.
 */
public interface CommunityJoinQrService {

	CommunityJoinQrImage generateJoinQr(UUID communityId);

	CommunityJoinQrMetadataResponse getJoinQrMetadata(UUID communityId);
}
