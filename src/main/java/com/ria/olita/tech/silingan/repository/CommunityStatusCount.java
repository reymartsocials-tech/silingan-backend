package com.ria.olita.tech.silingan.repository;

import com.ria.olita.tech.silingan.entity.CommunityStatus;

/** Communities with a given persisted status, and how many of them have an active administrator. */
public record CommunityStatusCount(CommunityStatus status, long total, long withAdmin) {
}
