package com.ria.olita.tech.silingan.service.otp;

import jakarta.servlet.http.HttpServletRequest;

import com.ria.olita.tech.silingan.dto.req.OtpVerifyRequest;
import com.ria.olita.tech.silingan.dto.res.OtpResendResponse;
import com.ria.olita.tech.silingan.dto.res.OtpStatusResponse;
import com.ria.olita.tech.silingan.dto.res.OtpVerificationResultResponse;
import com.ria.olita.tech.silingan.entity.User;

public interface OtpHandlerService {

	OtpResendResponse resendForAuthenticatedUser(HttpServletRequest request);

	OtpVerificationResultResponse verify(OtpVerifyRequest otpVerifyRequest);

	OtpStatusResponse statusForAuthenticatedUser();
}

