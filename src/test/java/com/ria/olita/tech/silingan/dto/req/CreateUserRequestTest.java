package com.ria.olita.tech.silingan.dto.req;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class CreateUserRequestTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void setUpValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void tearDownValidator() {
		factory.close();
	}

	private static CreateUserRequest.CreateUserRequestBuilder resident() {
		return CreateUserRequest.builder()
			.firstName("Juan")
			.lastName("Dela Cruz")
			.mobileNumber("+639171234567");
	}

	private static Set<ConstraintViolation<CreateUserRequest>> validate(CreateUserRequest request) {
		return validator.validate(request);
	}

	@Test
	void existingManualCommunityCodeClientsStillValidate() {
		assertThat(validate(resident().communityCode("MAPLERES-2026").build())).isEmpty();
	}

	@Test
	void invitationTokenAloneIsAccepted() {
		assertThat(validate(resident().invitationToken("Y8hVfM3wQk7xP2nR6cT9").build())).isEmpty();
	}

	@Test
	void supplyingBothSelectionMechanismsIsRejected() {
		Set<ConstraintViolation<CreateUserRequest>> violations = validate(
			resident().communityCode("MAPLERES-2026").invitationToken("Y8hVfM3wQk7xP2nR6cT9").build()
		);

		assertThat(violations).isNotEmpty();
		assertThat(violations.iterator().next().getMessage())
			.isEqualTo("Provide either a community code or an invitation token, but not both");
	}

	@Test
	void supplyingNeitherSelectionMechanismIsRejected() {
		assertThat(validate(resident().build())).isNotEmpty();
	}

	@Test
	void blankValuesDoNotCountAsASelection() {
		assertThat(validate(resident().communityCode("  ").invitationToken("  ").build())).isNotEmpty();
	}

	@Test
	void theInvitationTokenSurvivesTheDerivedCopyHelpers() {
		CreateUserRequest request = resident()
			.invitationToken("Y8hVfM3wQk7xP2nR6cT9")
			.build()
			.withUsername("jdelacruz")
			.withEmail("juan@example.com");

		assertThat(request.invitationToken()).isEqualTo("Y8hVfM3wQk7xP2nR6cT9");
		assertThat(request.communityCode()).isNull();
	}

	@Test
	void theCommunityCodeSurvivesTheDerivedCopyHelpers() {
		CreateUserRequest request = resident()
			.communityCode("MAPLERES-2026")
			.build()
			.withUsername("jdelacruz");

		assertThat(request.communityCode()).isEqualTo("MAPLERES-2026");
		assertThat(request.invitationToken()).isNull();
	}
}
