package com.ria.olita.tech.silingan.security.context;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.ria.olita.tech.silingan.security.context.InvitationActivationGate.Outcome;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gate exists to keep invitation activation off the per-request path, so the behaviour that
 * matters is: a settled identity stops being checked, an unsettled one keeps being checked
 * promptly, and nothing is ever cached in a way that permanently hides a new invitation.
 */
class InvitationActivationGateTest {

	private static final String SUBJECT = "4d1f0b2e-0000-0000-0000-000000000001";

	private InvitationActivationGate gate(Duration settledTtl, Duration unsettledTtl) {
		return new InvitationActivationGate(settledTtl, unsettledTtl, 1000);
	}

	private InvitationActivationGate gate() {
		return gate(Duration.ofMinutes(30), Duration.ofSeconds(30));
	}

	@Test
	void unknownSubjectIsChecked() {
		assertThat(gate().needsCheck(SUBJECT)).isTrue();
	}

	@Test
	void settledSubjectIsNotCheckedAgain() {
		InvitationActivationGate gate = gate();

		gate.record(SUBJECT, Outcome.SETTLED);

		assertThat(gate.needsCheck(SUBJECT)).isFalse();
	}

	@Test
	void settledResultExpiresSoTheCheckEventuallyRunsAgain() throws InterruptedException {
		InvitationActivationGate gate = gate(Duration.ofMillis(50), Duration.ofMinutes(30));

		gate.record(SUBJECT, Outcome.SETTLED);
		assertThat(gate.needsCheck(SUBJECT)).isFalse();

		Thread.sleep(150);

		assertThat(gate.needsCheck(SUBJECT)).isTrue();
	}

	@Test
	void unsettledResultIsRetriedAfterTheShorterTtl() throws InterruptedException {
		// An invitee who has not finished their Keycloak required actions must be re-checked
		// quickly; this delay is how long they wait for access after completing them.
		InvitationActivationGate gate = gate(Duration.ofMinutes(30), Duration.ofMillis(50));

		gate.record(SUBJECT, Outcome.UNSETTLED);
		assertThat(gate.needsCheck(SUBJECT)).isFalse();

		Thread.sleep(150);

		assertThat(gate.needsCheck(SUBJECT)).isTrue();
	}

	@Test
	void repeatedReadsDoNotExtendASettledEntry() throws InterruptedException {
		// Guards the expireAfterRead override: a user polling constantly would otherwise keep
		// refreshing their own entry and never pick up a newly issued invitation.
		InvitationActivationGate gate = gate(Duration.ofMillis(120), Duration.ofMinutes(30));
		gate.record(SUBJECT, Outcome.SETTLED);

		for (int i = 0; i < 6; i++) {
			Thread.sleep(30);
			gate.needsCheck(SUBJECT);
		}

		assertThat(gate.needsCheck(SUBJECT)).isTrue();
	}

	@Test
	void invalidationForcesAnImmediateRecheck() {
		InvitationActivationGate gate = gate();
		gate.record(SUBJECT, Outcome.SETTLED);

		gate.invalidate(SUBJECT);

		assertThat(gate.needsCheck(SUBJECT)).isTrue();
	}

	@Test
	void subjectsAreIsolatedFromEachOther() {
		InvitationActivationGate gate = gate();

		gate.record(SUBJECT, Outcome.SETTLED);

		assertThat(gate.needsCheck("some-other-subject")).isTrue();
	}

	@Test
	void blankSubjectFailsOpenAndIsNeverCached() {
		InvitationActivationGate gate = gate();

		gate.record(null, Outcome.SETTLED);
		gate.record("  ", Outcome.SETTLED);

		assertThat(gate.needsCheck(null)).isTrue();
		assertThat(gate.needsCheck("  ")).isTrue();
	}

	@Test
	void invalidatingABlankSubjectIsANoOp() {
		InvitationActivationGate gate = gate();
		gate.record(SUBJECT, Outcome.SETTLED);

		gate.invalidate(null);
		gate.invalidate("");

		assertThat(gate.needsCheck(SUBJECT)).isFalse();
	}
}
