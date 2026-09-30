package com.ria.olita.tech.silingan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "otp_verification_state", indexes = {
	@Index(name = "idx_otp_state_exp", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpVerificationState {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(name = "mobile_number", nullable = false, length = 20)
	private String mobileNumber;

	@Column(name = "otp_verified", nullable = false)
	private boolean otpVerified;

	@Column(name = "verified_at", nullable = false)
	private Instant verifiedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;
}
