package com.ria.olita.tech.silingan.service.auth;

import java.util.UUID;

import com.ria.olita.tech.silingan.dto.req.OtpVerifyRequest;
import com.ria.olita.tech.silingan.dto.res.LoginResponse;

public interface AuthenticationService {

	LoginResponse loginWithOtp(OtpVerifyRequest request,String userAgent);

	LoginResponse issueTokenForMobile(String mobileNumber, UUID communityId);
}

