package com.ria.olita.tech.silingan.service.email;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.InvitationType;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

/**
 * Context carried into Keycloak's {@code executeActionsEmail()} flow.
 *
 * <p>Keycloak has no notion of Silingan staff roles: {@link StaffRoleCode} values live only in
 * the Silingan database and are deliberately <b>not</b> realm roles. To personalise the
 * invitation email without writing a custom Keycloak SPI, the values below are written onto the
 * Keycloak user as plain user attributes right before the email is triggered. The custom email
 * theme ({@code email/html/executeActions.ftl}) then reads those attributes and branches on them
 * with FreeMarker conditionals.
 *
 * <p>The attribute names produced by {@link #toKeycloakAttributes()} must stay in sync with:
 * <ul>
 *   <li>{@code keycloak/config/silingan-user-profile.json} - declares them so Keycloak persists
 *       them instead of dropping them as unmanaged attributes</li>
 *   <li>{@code keycloak/themes/my-community-theme/email/} - reads them when rendering</li>
 * </ul>
 *
 * @param invitationType  routes to the staff or the resident template
 * @param roleCode        Silingan application role, {@code null} for non-staff invitations
 * @param communityId     community the invitation belongs to, kept for traceability
 * @param communityName   community name rendered in the email body
 */
public record InvitationEmailContext(
	InvitationType invitationType,
	StaffRoleCode roleCode,
	UUID communityId,
	String communityName
) {

	public InvitationEmailContext {
		invitationType = invitationType != null ? invitationType : InvitationType.RESIDENT;
	}

	/** Context for a staff invitation carrying a concrete Silingan staff role. */
	public static InvitationEmailContext staff(StaffRoleCode roleCode, UUID communityId, String communityName) {
		return new InvitationEmailContext(InvitationType.STAFF, roleCode, communityId, communityName);
	}

	/** Context for a resident/community invitation. */
	public static InvitationEmailContext resident(UUID communityId, String communityName) {
		return new InvitationEmailContext(InvitationType.RESIDENT, null, communityId, communityName);
	}

	/** Minimal context when only the invitation type is known. */
	public static InvitationEmailContext forType(InvitationType invitationType) {
		return new InvitationEmailContext(invitationType, null, null, null);
	}

	/**
	 * Render this context as Keycloak user attributes.
	 *
	 * <p>Blank values are omitted so a previously stored attribute is never overwritten with an
	 * empty string, which would make the FreeMarker fallbacks unreachable.
	 */
	public Map<String, List<String>> toKeycloakAttributes() {
		Map<String, List<String>> attributes = new LinkedHashMap<>();
		attributes.put("invitationType", List.of(invitationType.name()));
		if (roleCode != null) {
			attributes.put("roleCode", List.of(roleCode.name()));
			attributes.put("roleDisplayName", List.of(roleCode.displayName()));
		}
		if (communityId != null) {
			attributes.put("communityId", List.of(communityId.toString()));
		}
		if (communityName != null && !communityName.isBlank()) {
			attributes.put("communityName", List.of(communityName));
		}
		return attributes;
	}
}
