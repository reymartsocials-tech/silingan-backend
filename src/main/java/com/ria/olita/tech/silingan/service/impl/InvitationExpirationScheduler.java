package com.ria.olita.tech.silingan.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ria.olita.tech.silingan.entity.InvitationStatus;
import com.ria.olita.tech.silingan.repository.InvitationRepository;

/**
 * Scheduled task to mark old pending invitations as expired.
 *
 * <p>Invitations expire after 7 days (set at creation). This scheduler runs periodically to mark
 * any pending invitations that have passed their expiration time as {@link InvitationStatus#EXPIRED},
 * ensuring the database doesn't accumulate stale invitation records.
 *
 * <p>While expiration is also checked inline during creation (for deduplication) and during
 * activation (to block expired activations), this scheduler provides systematic cleanup of
 * invitations that were never acted upon.
 */
@Component
public class InvitationExpirationScheduler {

	private static final Logger log = LoggerFactory.getLogger(InvitationExpirationScheduler.class);

	private final InvitationRepository invitationRepository;

	public InvitationExpirationScheduler(InvitationRepository invitationRepository) {
		this.invitationRepository = invitationRepository;
	}

	/**
	 * Marks all pending invitations that have expired as EXPIRED.
	 *
	 * <p>Runs daily at 2 AM UTC. Invitations with a past {@code expiresAt} and status
	 * {@link InvitationStatus#PENDING} are transitioned to {@link InvitationStatus#EXPIRED}.
	 */
	@Scheduled(cron = "0 0 2 * * *")
	public void expireOldInvitations() {
		try {
			LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
			int count = invitationRepository.expireOldPendingInvitations(
				InvitationStatus.PENDING,
				InvitationStatus.EXPIRED,
				now
			);
			if (count > 0) {
				log.info("Marked {} old pending invitations as expired", count);
			}
		} catch (Exception e) {
			log.error("Failed to expire old invitations", e);
		}
	}
}
