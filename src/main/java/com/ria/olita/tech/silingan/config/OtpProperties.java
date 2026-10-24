package com.ria.olita.tech.silingan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration properties for OTP authentication.
 * Configurable via application.yml under the 'otp' prefix.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "otp")
public class OtpProperties {

    /**
     * Length of the generated OTP (default: 6 digits).
     */
    @NotNull
    @Min(4)
    @Max(10)
    private Integer length = 6;

    /**
     * OTP expiration time in minutes (default: 30 minutes).
     */
    @NotNull
    @Min(1)
    @Max(60)
    private Integer expirationMinutes = 30;

    /**
     * Maximum verification attempts allowed per OTP (default: 3).
     */
    @NotNull
    @Min(1)
    @Max(10)
    private Integer maxAttempts = 3;

    /**
     * Cooldown period in seconds before a new OTP can be requested (default: 60 seconds).
     */
    @NotNull
    @Min(30)
    @Max(300)
    private Integer cooldownSeconds = 60;

    /**
     * Validity window (minutes) for using an OTP verification proof in self-service registration.
     */
    @NotNull
    @Min(1)
    @Max(60)
    private Integer registrationProofMinutes = 10;


    /**
     * Cache configuration properties.
     */
    @NotNull
    private Cache cache = new Cache();

    @Getter
    @Setter
    public static class Cache {
        /**
         * Maximum number of OTP entries in cache.
         */
        @Min(100)
        @Max(1000000)
        private Integer maxSize = 10000;
    }
}

