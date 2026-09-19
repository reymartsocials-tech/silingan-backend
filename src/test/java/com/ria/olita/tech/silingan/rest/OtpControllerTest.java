package com.ria.olita.tech.silingan.rest;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.ria.olita.tech.silingan.dto.req.OtpVerifyRequest;
import com.ria.olita.tech.silingan.dto.res.OtpResponse;
import com.ria.olita.tech.silingan.service.otp.OtpHandlerService;
import com.ria.olita.tech.silingan.service.otp.OtpService;
import com.ria.olita.tech.silingan.service.otp.RegistrationOtpProofService;

import org.mockito.Mockito;

class OtpControllerTest {

    @Test
    void shouldMarkRegistrationProofAfterVerifyRegistration() {
        OtpService otpService = Mockito.mock(OtpService.class);
        OtpHandlerService otpHandlerService = Mockito.mock(OtpHandlerService.class);
        RegistrationOtpProofService registrationOtpProofService = Mockito.mock(RegistrationOtpProofService.class);

        OtpController controller = new OtpController(otpService, otpHandlerService, registrationOtpProofService);

        OtpVerifyRequest request = OtpVerifyRequest.builder()
                .mobileNumber("+639171234567")
                .otp("123456")
                .build();

        when(otpService.verifyOtp(Mockito.eq(request), Mockito.anyString()))
                .thenReturn(OtpResponse.verified());

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("User-Agent", "junit");

        controller.verifyOtpLegacy(request, httpRequest);

        verify(registrationOtpProofService).markVerifiedForRegistration("+639171234567");
    }
}

