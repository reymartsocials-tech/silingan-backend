package com.ria.olita.tech.silingan.security.context;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ria.olita.tech.silingan.exception.ApiError;
import com.ria.olita.tech.silingan.service.otp.OtpVerificationStateService;

import java.io.IOException;
import java.util.List;

import org.springframework.util.AntPathMatcher;

@Component
public class OtpVerificationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(OtpVerificationFilter.class);

	private final OtpVerificationStateService otpVerificationStateService;
	private final ObjectMapper objectMapper;
	private final List<String> excludedPatterns;
	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	public OtpVerificationFilter(OtpVerificationStateService otpVerificationStateService, ObjectMapper objectMapper) {
		this.otpVerificationStateService = otpVerificationStateService;
		this.objectMapper = objectMapper;
		this.excludedPatterns = List.of(
			"/api/v1/admin/communities/**",
			"/api/v1/public/**",
			"/api/v1/tenants/**",
			"/api/v1/auth/login/otp",
			"/api/v1/auth/otp/**",
			"/api/v1/communities/*/roles",
			"/api/v1/communities/*/roles/permissions",
			"/public/**",
			"/v3/api-docs/**",
			"/swagger-ui/**",
			"/swagger-ui.html",
			"/swagger-resources/**",
			"/webjars/**",
			"/error"
		);
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request,
	                                @NonNull HttpServletResponse response,
	                                @NonNull FilterChain filterChain) throws ServletException, IOException {

		if (!requiresOtp(request)) {
			filterChain.doFilter(request, response);
			return;
		}

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
			filterChain.doFilter(request, response);
			return;
		}

		Jwt jwt = jwtAuthenticationToken.getToken();
		String keycloakUserId = jwt.getClaimAsString("keycloakId");
		if (keycloakUserId == null || keycloakUserId.isBlank()) {
			keycloakUserId = jwt.getClaimAsString("sub");
		}
		if (keycloakUserId == null || keycloakUserId.isBlank()) {
			filterChain.doFilter(request, response);
			return;
		}

		boolean otpVerified = otpVerificationStateService.isVerified(keycloakUserId, jwt.getId())
			|| otpVerificationStateService.isVerifiedForUser(keycloakUserId);
		if (otpVerified) {
			filterChain.doFilter(request, response);
			return;
		}

		log.debug("Blocking request {} because OTP is not verified for subject {}", request.getRequestURI(), keycloakUserId);
		writeOtpRequiredResponse(request, response);
	}

	private boolean requiresOtp(HttpServletRequest request) {
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			return false;
		}

		String path = request.getRequestURI();
		if (path == null || path.isBlank()) {
			return false;
		}

		// OTP enforcement is only for backend API routes.
		if (!path.startsWith("/api/")) {
			return false;
		}

		return excludedPatterns.stream().noneMatch(pattern -> pathMatcher.match(pattern, path));
	}

	private void writeOtpRequiredResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);

		ApiError error = new ApiError("OTP_REQUIRED", "OTP verification is required.", request.getRequestURI());
		objectMapper.writeValue(response.getOutputStream(), error);
	}
}



