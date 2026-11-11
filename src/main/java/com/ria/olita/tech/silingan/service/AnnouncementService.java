package com.ria.olita.tech.silingan.service;

import com.ria.olita.tech.silingan.dto.req.AnnouncementFilterCriteria;
import com.ria.olita.tech.silingan.dto.req.CreateAnnouncementRequest;
import com.ria.olita.tech.silingan.dto.req.UpdateAnnouncementRequest;
import com.ria.olita.tech.silingan.dto.res.AnnouncementInteractionResponse;
import com.ria.olita.tech.silingan.dto.res.AnnouncementResponse;
import com.ria.olita.tech.silingan.dto.res.ResidentAnnouncementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AnnouncementService {

    AnnouncementResponse createAnnouncement(CreateAnnouncementRequest request);

    AnnouncementResponse updateAnnouncement(UUID id, UpdateAnnouncementRequest request);

    Page<AnnouncementResponse> getCommunityAnnouncements(UUID communityId, Pageable pageable);

    Page<AnnouncementResponse> getCommunityAnnouncementsWithFilters(UUID communityId, AnnouncementFilterCriteria criteria, Pageable pageable);

    Page<AnnouncementResponse> getActiveCommunityAnnouncements(UUID communityId, Pageable pageable);

    AnnouncementResponse getAnnouncementById(UUID id);

    void deleteAnnouncement(UUID id);
    
    AnnouncementResponse publishAnnouncement(UUID id);
    
    AnnouncementResponse unpublishAnnouncement(UUID id);

    /**
     * Published announcements of the authenticated resident's active community, newest publication
     * first. The community is derived from the security context, never from client input.
     */
    Page<ResidentAnnouncementResponse> getPublishedAnnouncementsForResident(Pageable pageable);

    ResidentAnnouncementResponse getPublishedAnnouncementForResident(UUID announcementId);

    /**
     * Idempotently records that the authenticated resident has read the announcement and returns
     * the complete interaction state.
     */
    AnnouncementInteractionResponse acknowledgeAnnouncement(UUID announcementId);

    /**
     * Idempotently records that the authenticated resident found the announcement helpful and
     * returns the complete interaction state.
     */
    AnnouncementInteractionResponse markAnnouncementHelpful(UUID announcementId);
}
