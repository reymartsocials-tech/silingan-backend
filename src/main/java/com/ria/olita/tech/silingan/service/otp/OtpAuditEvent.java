package com.ria.olita.tech.silingan.service.otp;

import java.time.Instant;

import lombok.Builder;
import lombok.Getter;

/**
 * Represents an OTP audit event for logging and tracking.
 */
@Getter
@Builder
public class OtpAuditEvent {

    public enum EventType {
        OTP_REQUESTED,
        OTP_SENT_SUCCESS,
        OTP_SENT_FAILED,
        OTP_VERIFICATION_SUCCESS,
        OTP_VERIFICATION_FAILED,
        OTP_EXPIRED,
        OTP_MAX_ATTEMPTS_EXCEEDED,
        OTP_COOLDOWN_BLOCKED
    }

    private final EventType eventType;
    private final String mobileNumber;
    private final Instant timestamp;
    private final String userAgent;
    private final Integer attemptNumber;
    private final String failureReason;

    /**
     * Masks the mobile number for logging (privacy).
     */
    public String getMaskedMobileNumber() {
        if (mobileNumber == null || mobileNumber.length() < 8) {
            return "****";
        }
        return mobileNumber.substring(0, 4) + "****" + mobileNumber.substring(mobileNumber.length() - 4);
    }
}

