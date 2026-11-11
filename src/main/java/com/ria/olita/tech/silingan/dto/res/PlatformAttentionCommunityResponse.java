package com.ria.olita.tech.silingan.dto.res;

import java.util.List;
import java.util.UUID;

/** {@code issue} is the highest-priority entry of {@code issues}. */
public record PlatformAttentionCommunityResponse(
	UUID communityId,
	String communityName,
	long residentCount,
	long adminCount,
	PlatformAttentionIssue issue,
	List<PlatformAttentionIssue> issues
) {
}
