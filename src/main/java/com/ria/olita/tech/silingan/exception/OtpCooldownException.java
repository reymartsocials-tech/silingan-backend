package com.ria.olita.tech.silingan.exception;

/**
 * Exception thrown when OTP request is made during cooldown period.
 */
public class OtpCooldownException extends ServiceException {

    private static final String CODE = "OTP_COOLDOWN";

    public OtpCooldownException(long remainingSeconds) {
        super(CODE, String.format("Please wait %d seconds before requesting a new OTP", remainingSeconds));
    }

}

