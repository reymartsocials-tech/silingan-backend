package com.ria.olita.tech.silingan.security.context;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Expiry;


/**
 * Decides whether a request still needs to run the invitation activation checks.
 *
 * <p>Invitation activation only ever has something to do immediately after an invitee finishes
 * their Keycloak required actions. It was previously re-run on <em>every</em> authenticated
 * request, which cost a database probe per request for every user forever, and a Keycloak round
 * trip for anyone with an outstanding invitation. This gate collapses that to roughly one check
 * per user per {@code settledTtl}.
 *
 * <p>Two different lifetimes, because the two outcomes mean different things:
 * <ul>
 *   <li>{@link Outcome#SETTLED} - nothing was pending. Cached for a long time; the only way new
 *       work appears is a freshly issued invitation, which the invitee picks up on their next
 *       login or by calling the explicit accept endpoint.</li>
 *   <li>{@link Outcome#UNSETTLED} - something is pending but not yet completable (required
 *       actions outstanding), or the check failed. Cached only briefly, so a user who is
 *       mid-onboarding still activates promptly without hammering Keycloak on every request.</li>
 * </ul>
 *
 * <p>Per-instance and non-authoritative by design: a miss only costs the work that used to happen
 * unconditionally, so the gate never needs to be correct across a cluster.
 */
@Component
public class InvitationActivationGate {

	public enum Outcome {
		/** No invitation work remains for this identity. */
		SETTLED,
		/** Work remains, or the check could not complete. Re-check soon. */
		UNSETTLED
	}

	private final Cache<String, Outcome> checked;

	public InvitationActivationGate(
		@Value("${app.invitations.activation-gate.settled-ttl:PT30M}") Duration settledTtl,
		@Value("${app.invitations.activation-gate.unsettled-ttl:PT30S}") Duration unsettledTtl,
		@Value("${app.invitations.activation-gate.max-size:50000}") long maximumSize
	) {
		this.checked = Caffeine.newBuilder()
			.maximumSize(maximumSize)
			.expireAfter(new Expiry<String, Outcome>() {
				@Override
				public long expireAfterCreate(String key, Outcome outcome, long currentTime) {
					return ttlNanos(outcome);
				}

				@Override
				public long expireAfterUpdate(String key, Outcome outcome,
				                              long currentTime, long currentDuration) {
					return ttlNanos(outcome);
				}

				@Override
				public long expireAfterRead(String key, Outcome outcome,
				                            long currentTime, long currentDuration) {
					// Reads must not extend the entry: a user making constant requests would
					// otherwise never re-check and could never pick up a new invitation.
					return currentDuration;
				}

				private long ttlNanos(Outcome outcome) {
					Duration ttl = outcome == Outcome.SETTLED ? settledTtl : unsettledTtl;
					return TimeUnit.NANOSECONDS.convert(ttl.toMillis(), TimeUnit.MILLISECONDS);
				}
			})
			.recordStats()
			.build();
	}

	/**
	 * @return {@code true} when the activation checks should run for this identity. Always true
	 *         for an unknown or blank subject, so an unidentifiable caller fails open into the
	 *         (correct, merely slower) full check.
	 */
	public boolean needsCheck(String keycloakUserId) {
		if (keycloakUserId == null || keycloakUserId.isBlank()) {
			return true;
		}
		return checked.getIfPresent(keycloakUserId) == null;
	}

	/**
	 * Records the result of a completed check. Blank subjects are ignored rather than cached
	 * under a shared key.
	 */
	public void record(String keycloakUserId, Outcome outcome) {
		if (keycloakUserId == null || keycloakUserId.isBlank()) {
			return;
		}
		checked.put(keycloakUserId, outcome);
	}

	/**
	 * Forces the next request for this identity to re-run the checks. Called when an invitation is
	 * issued, so an already-signed-in user does not have to wait out the settled TTL.
	 */
	public void invalidate(String keycloakUserId) {
		if (keycloakUserId == null || keycloakUserId.isBlank()) {
			return;
		}
		checked.invalidate(keycloakUserId);
	}
}
