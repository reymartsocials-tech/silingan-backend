package com.ria.olita.tech.silingan.service.otp;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.ria.olita.tech.silingan.service.otp.OtpAuditEvent.EventType;

/**
 * Service for auditing OTP-related events.
 * Logs events in a structured format for security monitoring and compliance.
 * 
 * Integration options:
 * - ELK Stack (Elasticsearch, Logstash, Kibana)
 * - Grafana Loki
 * - AWS CloudWatch
 * - Datadog / Splunk
 * 
 * Logs are output in JSON format using Logstash encoder for easy parsing.
 */
@Service
public class OtpAuditService {

    private static final Logger auditLog = LoggerFactory.getLogger("OTP_AUDIT");

    /**
     * Logs an OTP request event.
     */
    public void logOtpRequested(String mobileNumber, String userAgent) {
        OtpAuditEvent event = OtpAuditEvent.builder()
                .eventType(EventType.OTP_REQUESTED)
                .mobileNumber(mobileNumber)
                .timestamp(Instant.now())
                .userAgent(userAgent)
                .build();
        logEvent(event);
    }

    /**
     * Logs a successful OTP send event.
     */
    public void logOtpSentSuccess(String mobileNumber) {
        OtpAuditEvent event = OtpAuditEvent.builder()
                .eventType(EventType.OTP_SENT_SUCCESS)
                .mobileNumber(mobileNumber)
                .timestamp(Instant.now())
                .build();
        logEvent(event);
    }

    /**
     * Logs a failed OTP send event.
     */
    public void logOtpSentFailed(String mobileNumber, String reason) {
        OtpAuditEvent event = OtpAuditEvent.builder()
                .eventType(EventType.OTP_SENT_FAILED)
                .mobileNumber(mobileNumber)
                .timestamp(Instant.now())
                .failureReason(reason)
                .build();
        logEvent(event);
    }

    /**
     * Logs a successful OTP verification event.
     */
    public void logVerificationSuccess(String mobileNumber, String userAgent) {
        OtpAuditEvent event = OtpAuditEvent.builder()
                .eventType(EventType.OTP_VERIFICATION_SUCCESS)
                .mobileNumber(mobileNumber)
                .timestamp(Instant.now())
                .userAgent(userAgent)
                .build();
        logEvent(event);
    }

    /**
     * Logs a failed OTP verification event.
     */
    public void logVerificationFailed(String mobileNumber, int attemptNumber, String userAgent) {
        OtpAuditEvent event = OtpAuditEvent.builder()
                .eventType(EventType.OTP_VERIFICATION_FAILED)
                .mobileNumber(mobileNumber)
                .timestamp(Instant.now())
                .attemptNumber(attemptNumber)
                .userAgent(userAgent)
                .build();
        logEvent(event);
    }

    /**
     * Logs when max OTP attempts are exceeded.
     */
    public void logMaxAttemptsExceeded(String mobileNumber, String userAgent) {
        OtpAuditEvent event = OtpAuditEvent.builder()
                .eventType(EventType.OTP_MAX_ATTEMPTS_EXCEEDED)
                .mobileNumber(mobileNumber)
                .timestamp(Instant.now())
                .userAgent(userAgent)
                .build();
        logEvent(event);
    }

    /**
     * Logs when a request is blocked due to cooldown.
     */
    public void logCooldownBlocked(String mobileNumber, String userAgent) {
        OtpAuditEvent event = OtpAuditEvent.builder()
                .eventType(EventType.OTP_COOLDOWN_BLOCKED)
                .mobileNumber(mobileNumber)
                .timestamp(Instant.now())
                .userAgent(userAgent)
                .build();
        logEvent(event);
    }

    /**
     * Core logging method - uses MDC for structured JSON fields.
     * These fields are automatically included in JSON output by Logstash encoder.
     */
    private void logEvent(OtpAuditEvent event) {
        try {
            // Add structured fields to MDC for JSON logging
            MDC.put("otp_event_type", event.getEventType().name());
            MDC.put("mobile_number", event.getMaskedMobileNumber());
            MDC.put("event_timestamp", event.getTimestamp().toString());

            if (event.getUserAgent() != null) {
                MDC.put("user_agent", truncate(event.getUserAgent()));
            }
            if (event.getAttemptNumber() != null) {
                MDC.put("attempt_number", event.getAttemptNumber().toString());
            }
            if (event.getFailureReason() != null) {
                MDC.put("failure_reason", event.getFailureReason());
            }

            // Log message - fields in MDC are automatically added to JSON output
            String message = String.format("OTP %s for %s", 
                    event.getEventType().name().toLowerCase().replace("_", " "),
                    event.getMaskedMobileNumber());

            switch (event.getEventType()) {
                case OTP_VERIFICATION_SUCCESS, OTP_SENT_SUCCESS, OTP_REQUESTED -> 
                    auditLog.info(message);
                case OTP_VERIFICATION_FAILED, OTP_COOLDOWN_BLOCKED -> 
                    auditLog.warn(message);
                case OTP_MAX_ATTEMPTS_EXCEEDED, OTP_SENT_FAILED -> 
                    auditLog.error(message);
                default -> 
                    auditLog.info(message);
            }
        } finally {
            // Clear MDC to prevent leaking to other log entries
            MDC.remove("otp_event_type");
            MDC.remove("mobile_number");
            MDC.remove("event_timestamp");
            MDC.remove("client_ip");
            MDC.remove("user_agent");
            MDC.remove("attempt_number");
            MDC.remove("failure_reason");
        }
    }



    private String truncate(String value) {
        if (value == null || value.length() <= 100) {
            return value;
        }
        return value.substring(0, 100) + "...";
    }
}
