package com.ria.olita.tech.silingan.exception;

/**
 * Exception thrown when maximum OTP verification attempts have been exceeded.
 */
public class TooManyAttemptsException extends ServiceException {

  private static final String CODE = "OTP_ATTEMPTS_EXCEEDED";

    public TooManyAttemptsException() {
        super(CODE, "Maximum verification attempts exceeded. OTP has been invalidated. Please request a new one");
    }

}

