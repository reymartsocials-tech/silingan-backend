package com.ria.olita.tech.silingan.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.req.OtpCodeVerifyRequest;
import com.ria.olita.tech.silingan.dto.req.OtpRequest;
import com.ria.olita.tech.silingan.dto.req.OtpVerifyRequest;
import com.ria.olita.tech.silingan.dto.res.OtpResendResponse;
import com.ria.olita.tech.silingan.dto.res.OtpResponse;
import com.ria.olita.tech.silingan.dto.res.OtpStatusResponse;
import com.ria.olita.tech.silingan.dto.res.OtpVerificationResultResponse;
import com.ria.olita.tech.silingan.service.otp.OtpHandlerService;
import com.ria.olita.tech.silingan.service.otp.RegistrationOtpProofService;
import com.ria.olita.tech.silingan.service.otp.OtpService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth/otp")
@RequiredArgsConstructor
@Validated
@Tag(name = "OTP Authentication", description = "OTP request and verification endpoints")
public class OtpController {

	private static final Logger log = LoggerFactory.getLogger(OtpController.class);
	private final OtpService otpService;
	private final OtpHandlerService otpHandlerService;
	private final RegistrationOtpProofService registrationOtpProofService;

	@PostMapping("/resend")
	@Operation(summary = "Resend OTP", description = "Generates and sends OTP for the authenticated user")
	public ResponseEntity<OtpResendResponse> resendOtp(HttpServletRequest httpRequest) {
		log.debug("OTP resend requested by authenticated user");
		return ResponseEntity.ok(otpHandlerService.resendForAuthenticatedUser(httpRequest));
	}

	@PostMapping("/verify")
	@Operation(summary = "Verify OTP", description = "Verifies OTP for the authenticated user")
	public ResponseEntity<OtpVerificationResultResponse> verifyOtp(
		@Valid @RequestBody OtpCodeVerifyRequest request,
		HttpServletRequest httpRequest) {
		log.debug("OTP verification received for authenticated user");
		return ResponseEntity.ok(otpHandlerService.verifyForAuthenticatedUser(request.otp(), httpRequest));
	}

	@GetMapping("/status")
	@Operation(summary = "Get OTP status", description = "Returns OTP verification status for the authenticated user")
	public ResponseEntity<OtpStatusResponse> otpStatus() {
		return ResponseEntity.ok(otpHandlerService.statusForAuthenticatedUser());
	}

	@PostMapping("/request")
	@Operation(summary = "Request OTP for registration", description = "Generates and sends OTP to provided mobile number")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "OTP sent successfully",
			content = @Content(schema = @Schema(implementation = OtpResponse.class))),
		@ApiResponse(responseCode = "400", description = "Invalid mobile number format"),
		@ApiResponse(responseCode = "429", description = "Cooldown period active")
	})
	public ResponseEntity<OtpResponse> requestOtp(
		@Valid @RequestBody OtpRequest request,
		HttpServletRequest httpRequest) {
		log.debug("Registration OTP request received");
		String userAgent = httpRequest.getHeader("User-Agent");
		return ResponseEntity.ok(otpService.requestOtp(request, userAgent));
	}

	@PostMapping("/verify-registration")
	@Operation(summary = "Verify OTP for registration", description = "Verifies OTP for provided mobile number and marks registration proof")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "OTP verified successfully",
			content = @Content(schema = @Schema(implementation = OtpResponse.class))),
		@ApiResponse(responseCode = "400", description = "Invalid OTP"),
		@ApiResponse(responseCode = "410", description = "OTP expired"),
		@ApiResponse(responseCode = "429", description = "Maximum attempts exceeded")
	})
	public ResponseEntity<OtpResponse> verifyOtpLegacy(
		@Valid @RequestBody OtpVerifyRequest request,
		HttpServletRequest httpRequest) {
		log.debug("Registration OTP verification received");
		String userAgent = httpRequest.getHeader("User-Agent");
		OtpResponse response = otpService.verifyOtp(request, userAgent);
		registrationOtpProofService.markVerifiedForRegistration(request.getMobileNumber());
		return ResponseEntity.ok(response);
	}
}
