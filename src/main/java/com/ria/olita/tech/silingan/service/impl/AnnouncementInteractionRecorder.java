package com.ria.olita.tech.silingan.service.impl;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ria.olita.tech.silingan.entity.Announcement;
import com.ria.olita.tech.silingan.entity.AnnouncementAcknowledgement;
import com.ria.olita.tech.silingan.entity.AnnouncementHelpfulMark;
import com.ria.olita.tech.silingan.entity.UserCommunity;
import com.ria.olita.tech.silingan.repository.AnnouncementAcknowledgementRepository;
import com.ria.olita.tech.silingan.repository.AnnouncementHelpfulMarkRepository;

import lombok.RequiredArgsConstructor;

/**
 * Performs the interaction insert in its own transaction.
 *
 * <p>A concurrent duplicate loses the race on the unique constraint, and PostgreSQL aborts the
 * whole transaction when that happens. Running the insert in a suspended inner transaction lets
 * that failure roll back in isolation and be interpreted by the caller as "already recorded",
 * while the caller's transaction stays usable for reading back the resulting state.
 */
@Component
@RequiredArgsConstructor
public class AnnouncementInteractionRecorder {

	private final AnnouncementAcknowledgementRepository acknowledgementRepository;
	private final AnnouncementHelpfulMarkRepository helpfulMarkRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void insertAcknowledgement(Announcement announcement, UserCommunity membership) {
		acknowledgementRepository.saveAndFlush(AnnouncementAcknowledgement.builder()
			.announcement(announcement)
			.membership(membership)
			.build());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void insertHelpfulMark(Announcement announcement, UserCommunity membership) {
		helpfulMarkRepository.saveAndFlush(AnnouncementHelpfulMark.builder()
			.announcement(announcement)
			.membership(membership)
			.build());
	}
}
