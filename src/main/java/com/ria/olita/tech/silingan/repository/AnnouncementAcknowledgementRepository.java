package com.ria.olita.tech.silingan.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.ria.olita.tech.silingan.entity.AnnouncementAcknowledgement;

@Repository
public interface AnnouncementAcknowledgementRepository extends JpaRepository<AnnouncementAcknowledgement, UUID> {

	boolean existsByAnnouncementIdAndMembershipId(UUID announcementId, UUID membershipId);

	long countByAnnouncementId(UUID announcementId);

	/**
	 * Grouped totals for a whole page of announcements, so a list never issues one count query
	 * per row.
	 */
	@Query("SELECT a.announcementId AS announcementId, COUNT(a) AS total " +
		"FROM AnnouncementAcknowledgement a WHERE a.announcementId IN ?1 GROUP BY a.announcementId")
	List<AnnouncementInteractionCount> countByAnnouncementIdIn(Collection<UUID> announcementIds);

	/**
	 * The subset of the given announcements the resident has already acknowledged, resolved in a
	 * single query.
	 */
	@Query("SELECT a.announcementId FROM AnnouncementAcknowledgement a " +
		"WHERE a.membershipId = ?1 AND a.announcementId IN ?2")
	List<UUID> findAnnouncementIdsByMembershipIdAndAnnouncementIdIn(UUID membershipId, Collection<UUID> announcementIds);
}
