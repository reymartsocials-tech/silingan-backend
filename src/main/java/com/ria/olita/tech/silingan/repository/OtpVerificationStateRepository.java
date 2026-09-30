package com.ria.olita.tech.silingan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ria.olita.tech.silingan.entity.OtpVerificationState;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface OtpVerificationStateRepository extends JpaRepository<OtpVerificationState, UUID> {

	boolean existsByMobileNumberAndOtpVerifiedTrueAndExpiresAtAfter(String mobileNumber, Instant now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		UPDATE OtpVerificationState s
		SET s.otpVerified = false
		WHERE s.mobileNumber = :mobileNumber
		  AND s.otpVerified = true
		""")
	int markUnverifiedByMobileNumber(@Param("mobileNumber") String mobileNumber);
}
