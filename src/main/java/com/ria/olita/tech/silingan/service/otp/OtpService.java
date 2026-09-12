package com.ria.olita.tech.silingan.service.otp;

import com.ria.olita.tech.silingan.util.ContactNormalizer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.ria.olita.tech.silingan.config.OtpProperties;
import com.ria.olita.tech.silingan.config.SmsProperties;
import com.ria.olita.tech.silingan.dto.req.OtpRequest;
import com.ria.olita.tech.silingan.dto.req.OtpVerifyRequest;
import com.ria.olita.tech.silingan.dto.res.OtpResponse;
import com.ria.olita.tech.silingan.exception.ExpiredOtpException;
import com.ria.olita.tech.silingan.exception.InvalidOtpException;
import com.ria.olita.tech.silingan.exception.OtpCooldownException;
import com.ria.olita.tech.silingan.exception.TooManyAttemptsException;
import com.ria.olita.tech.silingan.service.sms.SmsService;

import lombok.RequiredArgsConstructor;

/**
 * Service for OTP generation, storage, and verification.
 * Implements secure OTP handling with:
 * - SHA-256 hashing for OTP storage
 * - Rate limiting via cooldown cache
 * - Brute-force protection via attempt tracking
 * - Single-use OTPs that are invalidated after verification
 * - Audit logging for all OTP events
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OtpProperties otpProperties;
    private final SmsProperties smsProperties;
    private final Cache<String, OtpData> otpCache;
    private final Cache<String, Long> cooldownCache;
    private final SmsService smsService;
    private final OtpAuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generates and sends a new OTP to the specified mobile number.
     * 
     * @param request the OTP request containing the mobile number
     * @param ipAddress client IP address for audit logging
     * @param userAgent client user agent for audit logging
     * @return response indicating success
     * @throws OtpCooldownException if the cooldown period has not elapsed
     */
    public OtpResponse requestOtp(OtpRequest request, String ipAddress, String userAgent) {
        String normalizedNumber = normalizePhoneNumber(request.getMobileNumber());

        // Audit: OTP requested
        auditService.logOtpRequested(normalizedNumber, ipAddress, userAgent);

        // Check cooldown
        try {
            checkCooldown(normalizedNumber);
        } catch (OtpCooldownException e) {
            auditService.logCooldownBlocked(normalizedNumber, ipAddress, userAgent);
            throw e;
        }

        // Generate new OTP
        String otp = generateOtp();
        String hashedOtp = hashOtp(otp);

        // Store hashed OTP in cache
        OtpData otpData = OtpData.builder()
                .hashedOtp(hashedOtp)
                .attemptCount(0)
                .createdAt(Instant.now())
                .mobileNumber(normalizedNumber)
                .build();

        otpCache.put(normalizedNumber, otpData);

        // Set cooldown
        cooldownCache.put(normalizedNumber, System.currentTimeMillis());

        // Send OTP via SMS
        boolean sent = smsService.sendOtp(normalizedNumber, otp);
        if (sent) {
            auditService.logOtpSentSuccess(normalizedNumber);
        } else {
            auditService.logOtpSentFailed(normalizedNumber, "SMS delivery failed");
            log.warn("Failed to send OTP SMS to {}", maskPhoneNumber(normalizedNumber));
        }

        log.info("OTP generated and sent for mobile: {}", maskPhoneNumber(normalizedNumber));

        return OtpResponse.otpSentWithCooldown(otpProperties.getCooldownSeconds());
    }

    /**
     * Verifies an OTP for the specified mobile number.
     * 
     * @param request the verification request containing mobile number and OTP
     * @param ipAddress client IP address for audit logging
     * @param userAgent client user agent for audit logging
     * @return response indicating successful verification
     * @throws ExpiredOtpException      if no OTP exists for the number
     * @throws TooManyAttemptsException if maximum attempts have been exceeded
     * @throws InvalidOtpException      if the OTP is invalid
     */
    public OtpResponse verifyOtp(OtpVerifyRequest request, String ipAddress, String userAgent) {
        String normalizedNumber = normalizePhoneNumber(request.getMobileNumber());

        // Retrieve OTP data from cache
        OtpData otpData = otpCache.getIfPresent(normalizedNumber);

        // Check if OTP exists (not expired)
        if (otpData == null) {
            log.warn("OTP verification attempt for non-existent/expired OTP: {}", 
                    maskPhoneNumber(normalizedNumber));
            auditService.logVerificationFailed(normalizedNumber, 0, ipAddress, userAgent);
            throw new ExpiredOtpException();
        }

        // Check if max attempts exceeded
        if (otpData.hasExceededAttempts(otpProperties.getMaxAttempts())) {
            log.warn("OTP max attempts exceeded for: {}", maskPhoneNumber(normalizedNumber));
            otpCache.invalidate(normalizedNumber);
            auditService.logMaxAttemptsExceeded(normalizedNumber, ipAddress, userAgent);
            throw new TooManyAttemptsException();
        }

        if (smsProperties.isBypassSending()) {
            // In bypass mode we still require a requested OTP challenge, but accept any code.
            otpCache.invalidate(normalizedNumber);
            auditService.logVerificationSuccess(normalizedNumber, ipAddress, userAgent);
            log.warn("SMS bypass enabled: accepting OTP verification without code check for {}",
                    maskPhoneNumber(normalizedNumber));
            return OtpResponse.verified();
        }

        // Hash the provided OTP and compare
        String hashedInput = hashOtp(request.getOtp());

        if (!hashedInput.equals(otpData.getHashedOtp())) {
            // Increment attempt count
            int newAttemptCount = otpData.incrementAttempts();
            int remainingAttempts = otpProperties.getMaxAttempts() - newAttemptCount;

            log.warn("Invalid OTP attempt ({}/{}) for: {}",
                    newAttemptCount,
                    otpProperties.getMaxAttempts(),
                    maskPhoneNumber(normalizedNumber));

            auditService.logVerificationFailed(normalizedNumber, newAttemptCount, ipAddress, userAgent);

            // Check if this was the last attempt
            if (remainingAttempts <= 0) {
                otpCache.invalidate(normalizedNumber);
                auditService.logMaxAttemptsExceeded(normalizedNumber, ipAddress, userAgent);
                throw new TooManyAttemptsException();
            }

            throw new InvalidOtpException(remainingAttempts);
        }

        // OTP is valid - invalidate it immediately (single-use)
        otpCache.invalidate(normalizedNumber);

        // Audit: Verification success
        auditService.logVerificationSuccess(normalizedNumber, ipAddress, userAgent);

        log.info("OTP verified successfully for: {}", maskPhoneNumber(normalizedNumber));

        return OtpResponse.verified();
    }

    /**
     * Checks if the mobile number is in cooldown period.
     *
     * @param mobileNumber the mobile number to check
     * @throws OtpCooldownException if cooldown is active
     */
    private void checkCooldown(String mobileNumber) {
        Long lastRequestTime = cooldownCache.getIfPresent(mobileNumber);

        if (lastRequestTime != null) {
            long elapsedSeconds = (System.currentTimeMillis() - lastRequestTime) / 1000;
            long remainingSeconds = otpProperties.getCooldownSeconds() - elapsedSeconds;

            if (remainingSeconds > 0) {
                log.debug("OTP request cooldown active for {} ({} seconds remaining)",
                        maskPhoneNumber(mobileNumber), remainingSeconds);
                throw new OtpCooldownException(remainingSeconds);
            }
        }
    }

    /**
     * Generates a secure random numeric OTP.
     *
     * @return the generated OTP string
     */
    private String generateOtp() {
        int length = otpProperties.getLength();
        StringBuilder otp = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            otp.append(secureRandom.nextInt(10));
        }

        return otp.toString();
    }

    /**
     * Hashes an OTP using SHA-256.
     *
     * @param otp the plain OTP to hash
     * @return the hashed OTP as a hex string
     */
    private String hashOtp(String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(otp.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available in Java
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Normalizes a phone number to international format.
     * Converts 09XXXXXXXXX to +639XXXXXXXXX
     *
     * @param phoneNumber the phone number to normalize
     * @return normalized phone number
     */
    private String normalizePhoneNumber(String phoneNumber) {
        return ContactNormalizer.normalizeMobileNumber(phoneNumber);
    }

    /**
     * Masks a phone number for logging purposes.
     * Example: +639171234567 -> +6391****4567
     */
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 8) {
            return "****";
        }
        int len = phoneNumber.length();
        return phoneNumber.substring(0, 4) + "****" + phoneNumber.substring(len - 4);
    }
}

