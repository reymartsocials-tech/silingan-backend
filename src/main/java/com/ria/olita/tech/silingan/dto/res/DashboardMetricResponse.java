package com.ria.olita.tech.silingan.dto.res;

/**
 * A dashboard metric card. {@code percentageChange} is null, and {@code comparisonAvailable} false,
 * whenever the previous period cannot be reconstructed reliably or is zero; the client shows "N/A".
 */
public record DashboardMetricResponse(
	long value,
	Double percentageChange,
	boolean comparisonAvailable
) {

	public static DashboardMetricResponse unavailable(long value) {
		return new DashboardMetricResponse(value, null, false);
	}
}
