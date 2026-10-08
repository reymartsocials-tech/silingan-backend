package com.ria.olita.tech.silingan.service.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.ria.olita.tech.silingan.entity.InvitationType;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

/**
 * Verifies the Keycloak user attributes that drive the custom email theme.
 *
 * <p>These attribute names are a contract shared with
 * {@code keycloak/themes/my-community-theme/email/} and
 * {@code keycloak/config/silingan-user-profile.json}.
 */
class InvitationEmailContextTest {

	private static final UUID COMMUNITY_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

	@ParameterizedTest
	@EnumSource(StaffRoleCode.class)
	void staffContextExposesEveryStaffRoleCode(StaffRoleCode roleCode) {
		var attributes = InvitationEmailContext
			.staff(roleCode, COMMUNITY_ID, "Sunrise Village")
			.toKeycloakAttributes();

		assertThat(attributes).containsEntry("invitationType", List.of("STAFF"));
		assertThat(attributes).containsEntry("roleCode", List.of(roleCode.name()));
		assertThat(attributes).containsEntry("roleDisplayName", List.of(roleCode.displayName()));
		assertThat(attributes).containsEntry("communityId", List.of(COMMUNITY_ID.toString()));
		assertThat(attributes).containsEntry("communityName", List.of("Sunrise Village"));
	}

	@Test
	void residentContextOmitsStaffRoleAttributes() {
		var attributes = InvitationEmailContext
			.resident(COMMUNITY_ID, "Sunrise Village")
			.toKeycloakAttributes();

		assertThat(attributes).containsEntry("invitationType", List.of("RESIDENT"));
		assertThat(attributes).doesNotContainKeys("roleCode", "roleDisplayName");
	}

	@Test
	void blankCommunityNameIsOmittedSoTemplateFallbackApplies() {
		var attributes = InvitationEmailContext
			.staff(StaffRoleCode.PMO_STAFF, null, "   ")
			.toKeycloakAttributes();

		assertThat(attributes).doesNotContainKeys("communityName", "communityId");
		assertThat(attributes).containsEntry("roleCode", List.of("PMO_STAFF"));
	}

	@Test
	void nullInvitationTypeDefaultsToResident() {
		var attributes = InvitationEmailContext.forType(null).toKeycloakAttributes();

		assertThat(attributes).containsEntry("invitationType", List.of("RESIDENT"));
	}

	@Test
	void forTypeStaffKeepsStaffRouting() {
		var attributes = InvitationEmailContext.forType(InvitationType.STAFF).toKeycloakAttributes();

		assertThat(attributes).containsEntry("invitationType", List.of("STAFF"));
	}
}
