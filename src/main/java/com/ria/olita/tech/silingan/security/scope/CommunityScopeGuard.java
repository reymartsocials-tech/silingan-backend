package com.ria.olita.tech.silingan.security.scope;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ria.olita.tech.silingan.exception.ForbiddenException;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Authoritative tenant boundary check.
 *
 * <p>Realm roles such as {@code COMMUNITY_ADMIN} are <em>not</em> bound to a community, so a
 * community-scoped endpoint that only checks the realm role lets an admin of community A operate on
 * community B by changing the path variable. Every community-scoped operation must therefore assert
 * that the caller actually belongs to the community identified by the request.
 *
 * <p>Membership is resolved from the database rather than from the {@code communityId} JWT claim,
 * because the claim originates from a Keycloak user attribute and must not be treated as trusted
 * authorization input.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommunityScopeGuard {

	private final UserCommunityRepository userCommunityRepository;

	/**
	 * @throws ForbiddenException when the caller may not act on the given community.
	 */
	public void assertAccess(UUID communityId) {
		if (UserContextHolder.isPlatformAdmin()) {
			return;
		}

		if (communityId == null) {
			throw new ForbiddenException("Community scope could not be resolved for this request");
		}

		UserContext context = UserContextHolder.get();
		if (context == null || context.userId() == null) {
			throw new ForbiddenException("No authenticated user context");
		}

		if (!hasMembership(context, communityId)) {
			log.warn(
				"Cross-community access denied: user={} tokenCommunity={} requestedCommunity={}",
				context.userId(), context.communityId(), communityId
			);
			throw new ForbiddenException("You do not have access to this community");
		}
	}

	/**
	 * Non-throwing variant for callers that need to branch on the result.
	 */
	public boolean hasAccess(UUID communityId) {
		if (UserContextHolder.isPlatformAdmin()) {
			return true;
		}

		UserContext context = UserContextHolder.get();
		if (communityId == null || context == null || context.userId() == null) {
			return false;
		}

		return hasMembership(context, communityId);
	}

	private boolean hasMembership(UserContext context, UUID communityId) {
		UUID userId;
		try {
			userId = UUID.fromString(context.userId());
		} catch (IllegalArgumentException ex) {
			throw new ForbiddenException("Invalid authenticated user context");
		}

		return userCommunityRepository.findByUserIdAndCommunityId(userId, communityId).isPresent();
	}
}
