package com.ria.olita.tech.silingan.service.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ria.olita.tech.silingan.config.OtpProperties;
import com.ria.olita.tech.silingan.config.SmsProperties;
import com.ria.olita.tech.silingan.dto.req.OtpRequest;
import com.ria.olita.tech.silingan.dto.req.OtpVerifyRequest;
import com.ria.olita.tech.silingan.dto.res.OtpResponse;
import com.ria.olita.tech.silingan.exception.ExpiredOtpException;
import com.ria.olita.tech.silingan.exception.InvalidOtpException;
import com.ria.olita.tech.silingan.service.sms.SmsService;

class OtpServiceBypassTest {

    @Test
    void shouldVerifyAnyOtpWhenSmsBypassEnabledAndChallengeExists() {
        OtpService service = buildService(true);

        service.requestOtp(new OtpRequest("+639171234567"), "junit");

        OtpResponse response = service.verifyOtp(
                new OtpVerifyRequest("+639171234567", "000000"),
                "junit");

        assertThat(response.getMessage()).isEqualTo("OTP verified successfully");

        // Proof that OTP remains single-use even in bypass mode.
        assertThrows(ExpiredOtpException.class,
                () -> service.verifyOtp(new OtpVerifyRequest("+639171234567", "111111"), "junit"));
    }

    @Test
    void shouldStillFailInvalidOtpWhenSmsBypassDisabled() {
        OtpService service = buildService(false);

        service.requestOtp(new OtpRequest("+639171234567"), "junit");

        assertThrows(InvalidOtpException.class,
                () -> service.verifyOtp(new OtpVerifyRequest("+639171234567", "000000"), "junit"));
    }

    private OtpService buildService(boolean bypassSending) {
        OtpProperties otpProperties = new OtpProperties();
        otpProperties.setLength(6);
        otpProperties.setCooldownSeconds(60);
        otpProperties.setMaxAttempts(3);

        SmsProperties smsProperties = new SmsProperties();
        smsProperties.setBypassSending(bypassSending);

        Cache<String, OtpData> otpCache = Caffeine.newBuilder().maximumSize(100).build();
        Cache<String, Long> cooldownCache = Caffeine.newBuilder().maximumSize(100).build();

        SmsService smsService = Mockito.mock(SmsService.class);
        when(smsService.sendOtp(anyString(), anyString())).thenReturn(true);

        OtpAuditService auditService = Mockito.mock(OtpAuditService.class);

        OtpService service = new OtpService(otpProperties, smsProperties, otpCache, cooldownCache, smsService, auditService);

        // Ensure request flow still audits and uses the transport service.
        service.requestOtp(new OtpRequest("+639171234568"), "junit");
        verify(auditService).logOtpRequested("+639171234568", "junit");

        return service;
    }
}

