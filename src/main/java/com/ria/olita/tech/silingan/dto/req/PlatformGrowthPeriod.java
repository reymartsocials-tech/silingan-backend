package com.ria.olita.tech.silingan.dto.req;

import com.ria.olita.tech.silingan.exception.ValidationException;

import lombok.Getter;

/** Bounded, API-level growth windows; the wire value is matched exactly and never defaulted silently. */
@Getter
public enum PlatformGrowthPeriod {
	SIX_MONTHS("6_MONTHS", 6),
	TWELVE_MONTHS("12_MONTHS", 12);

	public static final String DEFAULT_VALUE = "6_MONTHS";

	private final String value;
	private final int months;

	PlatformGrowthPeriod(String value, int months) {
		this.value = value;
		this.months = months;
	}

	public static PlatformGrowthPeriod fromValue(String value) {
		for (PlatformGrowthPeriod period : values()) {
			if (period.value.equals(value)) {
				return period;
			}
		}
		throw new ValidationException("Unsupported growthPeriod. Supported values: 6_MONTHS, 12_MONTHS");
	}
}
