package com.ria.olita.tech.silingan.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class CommunityJoinInvitationTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 10, 12, 0);

	private static CommunityJoinInvitation invitation(
		CommunityJoinInvitationStatus status, LocalDateTime expiresAt, Integer maxUses, int usedCount
	) {
		return CommunityJoinInvitation.builder()
			.status(status)
			.expiresAt(expiresAt)
			.maxUses(maxUses)
			.usedCount(usedCount)
			.build();
	}

	@Test
	void isActiveWhenNotRevokedNotExpiredAndUnderTheUsageLimit() {
		CommunityJoinInvitation invitation =
			invitation(CommunityJoinInvitationStatus.ACTIVE, NOW.plusDays(1), 100, 5);

		assertThat(invitation.isUsable(NOW)).isTrue();
		assertThat(invitation.effectiveStatus(NOW)).isEqualTo(CommunityJoinInvitationStatus.ACTIVE);
	}

	@Test
	void reportsExpiredEvenWhileStoredAsActive() {
		// Validation must never depend on a scheduled job having updated the stored status.
		CommunityJoinInvitation invitation =
			invitation(CommunityJoinInvitationStatus.ACTIVE, NOW.minusMinutes(1), 100, 0);

		assertThat(invitation.effectiveStatus(NOW)).isEqualTo(CommunityJoinInvitationStatus.EXPIRED);
		assertThat(invitation.isUsable(NOW)).isFalse();
	}

	@Test
	void treatsTheExactExpiryInstantAsExpired() {
		CommunityJoinInvitation invitation =
			invitation(CommunityJoinInvitationStatus.ACTIVE, NOW, 100, 0);

		assertThat(invitation.isUsable(NOW)).isFalse();
	}

	@Test
	void reportsExhaustedWhenTheUsageLimitIsReached() {
		CommunityJoinInvitation invitation =
			invitation(CommunityJoinInvitationStatus.ACTIVE, NOW.plusDays(1), 2, 2);

		assertThat(invitation.effectiveStatus(NOW)).isEqualTo(CommunityJoinInvitationStatus.EXHAUSTED);
		assertThat(invitation.isUsable(NOW)).isFalse();
	}

	@Test
	void revocationWinsOverExpirationAndExhaustion() {
		CommunityJoinInvitation invitation =
			invitation(CommunityJoinInvitationStatus.REVOKED, NOW.minusDays(1), 2, 2);

		assertThat(invitation.effectiveStatus(NOW)).isEqualTo(CommunityJoinInvitationStatus.REVOKED);
	}

	@Test
	void unlimitedUsesAreNeverExhausted() {
		CommunityJoinInvitation invitation =
			invitation(CommunityJoinInvitationStatus.ACTIVE, NOW.plusDays(1), null, 10_000);

		assertThat(invitation.isExhausted()).isFalse();
		assertThat(invitation.isUsable(NOW)).isTrue();
	}

	@Test
	void doesNotExposeFieldsThroughToString() {
		// Lombok must not generate a toString that could leak the token hash into a log line.
		CommunityJoinInvitation invitation =
			invitation(CommunityJoinInvitationStatus.ACTIVE, NOW.plusDays(1), 100, 0);
		invitation.setTokenHash("deadbeef");

		assertThat(invitation.toString()).doesNotContain("deadbeef");
	}
}
