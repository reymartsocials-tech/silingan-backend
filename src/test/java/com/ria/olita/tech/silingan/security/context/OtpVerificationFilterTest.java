package com.ria.olita.tech.silingan.security.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ria.olita.tech.silingan.service.otp.OtpVerificationStateService;

import java.time.Instant;

import org.mockito.Mockito;

class OtpVerificationFilterTest {

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void shouldBlockProtectedPathWhenOtpNotVerified() throws Exception {
		OtpVerificationStateService stateService = Mockito.mock(OtpVerificationStateService.class);
		when(stateService.isVerified("+639171234567")).thenReturn(false);

		OtpVerificationFilter filter = new OtpVerificationFilter(stateService, new ObjectMapper());

		Jwt jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.expiresAt(Instant.now().plusSeconds(300))
			.claim("mobileNumber", "+639171234567")
			.claim("scope", "openid")
			.build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payments/1");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentAsString()).contains("OTP_REQUIRED");
	}

	@Test
	void shouldAllowProtectedPathWhenOtpVerified() throws Exception {
		OtpVerificationStateService stateService = Mockito.mock(OtpVerificationStateService.class);
		when(stateService.isVerified("+639171234567")).thenReturn(true);

		OtpVerificationFilter filter = new OtpVerificationFilter(stateService, new ObjectMapper());

		Jwt jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.expiresAt(Instant.now().plusSeconds(300))
			.claim("mobileNumber", "+639171234567")
			.claim("scope", "openid")
			.build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payments/1");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void shouldBypassOtpCheckForOtpEndpoints() throws Exception {
		OtpVerificationStateService stateService = Mockito.mock(OtpVerificationStateService.class);
		when(stateService.isVerified("+639171234567")).thenReturn(false);

		OtpVerificationFilter filter = new OtpVerificationFilter(stateService, new ObjectMapper());

		Jwt jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.expiresAt(Instant.now().plusSeconds(300))
			.claim("mobileNumber", "+639171234567")
			.claim("scope", "openid")
			.build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/otp/status");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void shouldBypassOtpCheckForRootPath() throws Exception {
		OtpVerificationStateService stateService = Mockito.mock(OtpVerificationStateService.class);
		OtpVerificationFilter filter = new OtpVerificationFilter(stateService, new ObjectMapper());

		Jwt jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.expiresAt(Instant.now().plusSeconds(300))
			.claim("mobileNumber", "+639171234567")
			.claim("scope", "openid")
			.build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
	}

	@Test
	void shouldAllowProtectedPathWhenMobileClaimMissing() throws Exception {
		OtpVerificationStateService stateService = Mockito.mock(OtpVerificationStateService.class);

		OtpVerificationFilter filter = new OtpVerificationFilter(stateService, new ObjectMapper());

		Jwt jwt = Jwt.withTokenValue("token")
			.header("alg", "none")
			.expiresAt(Instant.now().plusSeconds(300))
			.claim("scope", "openid")
			.build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/payments/1");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		assertThat(response.getStatus()).isEqualTo(200);
	}
}
