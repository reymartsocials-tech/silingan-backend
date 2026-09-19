package com.ria.olita.tech.silingan.service.otp;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;

/**
 * Represents OTP data stored in cache.
 * Contains hashed OTP value and verification attempt tracking.
 */
@Getter
@Builder
public class OtpData {

    /**
     * SHA-256 hashed OTP value.
     * Plain OTP is never stored in memory for security.
     */
    private final String hashedOtp;

    /**
     * Number of verification attempts made.
     */
    private int attemptCount;

    /**
     * Timestamp when the OTP was created.
     */
    private final Instant createdAt;

    /**
     * Mobile number associated with this OTP.
     */
    private final String mobileNumber;

    /**
     * Increments the attempt count and returns the new count.
     *
     * @return updated attempt count
     */
    public int incrementAttempts() {
        return ++attemptCount;
    }

    /**
     * Checks if the maximum number of attempts has been exceeded.
     *
     * @param maxAttempts maximum allowed attempts
     * @return true if attempts to exceed the limit
     */
    public boolean hasExceededAttempts(int maxAttempts) {
        return attemptCount >= maxAttempts;
    }
}

