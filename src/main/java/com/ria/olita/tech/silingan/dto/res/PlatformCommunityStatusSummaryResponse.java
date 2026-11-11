package com.ria.olita.tech.silingan.dto.res;

/** Mutually exclusive groups: {@code active + needsAdmin + inactive == total}. */
public record PlatformCommunityStatusSummaryResponse(long total, long active, long needsAdmin, long inactive) {
}
