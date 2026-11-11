package com.ria.olita.tech.silingan.dto.res;

/** Communities created in the calendar month {@code period} (format {@code YYYY-MM}). */
public record PlatformGrowthPointResponse(String period, long value) {
}
