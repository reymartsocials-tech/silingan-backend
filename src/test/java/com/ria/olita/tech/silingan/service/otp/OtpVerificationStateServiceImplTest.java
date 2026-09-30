package com.ria.olita.tech.silingan.service.otp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.ria.olita.tech.silingan.entity.OtpVerificationState;
import com.ria.olita.tech.silingan.repository.OtpVerificationStateRepository;

import java.time.Instant;

class OtpVerificationStateServiceImplTest {

	@Test
	void shouldReturnTrueWhenMobileHasActiveOtpVerification() {
		OtpVerificationStateRepository repository = Mockito.mock(OtpVerificationStateRepository.class);
		OtpVerificationStateServiceImpl service = new OtpVerificationStateServiceImpl(repository);
		String mobileNumber = "+639171234567";

		when(repository.existsByMobileNumberAndOtpVerifiedTrueAndExpiresAtAfter(eq(mobileNumber), any(Instant.class)))
			.thenReturn(true);

		assertThat(service.isVerified(mobileNumber)).isTrue();
	}

	@Test
	void shouldPersistVerifiedState() {
		OtpVerificationStateRepository repository = Mockito.mock(OtpVerificationStateRepository.class);
		OtpVerificationStateServiceImpl service = new OtpVerificationStateServiceImpl(repository);
		String mobileNumber = "09171234567";

		service.markVerified(mobileNumber, Instant.now().plusSeconds(60));

		verify(repository).markUnverifiedByMobileNumber("+639171234567");
		ArgumentCaptor<OtpVerificationState> captor = ArgumentCaptor.forClass(OtpVerificationState.class);
		verify(repository).save(captor.capture());
		assertThat(captor.getValue().getMobileNumber()).isEqualTo("+639171234567");
		assertThat(captor.getValue().isOtpVerified()).isTrue();
	}

	@Test
	void shouldClearVerificationByMobileNumber() {
		OtpVerificationStateRepository repository = Mockito.mock(OtpVerificationStateRepository.class);
		OtpVerificationStateServiceImpl service = new OtpVerificationStateServiceImpl(repository);
		String mobileNumber = "09171234567";

		service.clearVerification(mobileNumber);

		verify(repository).markUnverifiedByMobileNumber("+639171234567");
		verify(repository, never()).save(any());
	}
}
