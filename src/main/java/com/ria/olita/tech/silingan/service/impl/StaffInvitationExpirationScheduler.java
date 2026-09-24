package com.ria.olita.tech.silingan.service.impl;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ria.olita.tech.silingan.service.StaffInvitationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled task to mark expired staff invitations.
 *
 * <p>Runs daily at midnight UTC. The task is idempotent: calling it multiple times
 * on the same data produces the same result (only updates PENDING invitations with
 * expiration time in the past).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffInvitationExpirationScheduler {

	private final StaffInvitationService staffInvitationService;

	/**
	 * Expire old pending staff invitations.
	 * Runs daily at 00:00:00 UTC (cron: "0 0 * * * UTC").
	 */
	@Scheduled(cron = "0 0 * * * UTC", zone = "UTC")
	public void expireOldInvitations() {
		log.info("Starting scheduled expiration of old staff invitations");
		try {
			int count = staffInvitationService.expireOldInvitations();
			log.info("Scheduled expiration complete: {} invitations marked as expired", count);
		} catch (Exception e) {
			log.error("Error during scheduled invitation expiration", e);
		}
	}
}
