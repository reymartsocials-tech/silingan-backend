package com.ria.olita.tech.silingan.service;

import com.ria.olita.tech.silingan.dto.req.CreateUserRequest;
import com.ria.olita.tech.silingan.entity.InvitationType;
import com.ria.olita.tech.silingan.service.email.InvitationEmailContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface KeycloakService {

	String createUser(CreateUserRequest request, UUID communityId);

	void updateUserAttributes(String keycloakUserId, Map<String, List<String>> attributes);

	Map<String, List<String>> getUserAttributes(String keycloakUserId);

	List<String> getRealmRoles(String keycloakUserId);

	boolean isUserEnabled(String keycloakUserId);

	Optional<String> findUserIdByEmail(String email);

	void assignRealmRole(String keycloakUserId, String roleName);

	/**
	 * Send invitation email via Keycloak using a specific invitation type.
	 *
	 * The invitation type determines which email template is used, allowing different
	 * messaging and formatting for resident vs staff invitations.
	 *
	 * @param keycloakUserId the Keycloak user ID
	 * @param requiredActions list of required actions (e.g., VERIFY_EMAIL, UPDATE_PASSWORD)
	 * @param invitationType the type of invitation (determines email template)
	 */
	void sendRequiredActionsEmail(String keycloakUserId, List<String> requiredActions,
		InvitationType invitationType);

	/**
	 * Send an invitation email via Keycloak's {@code executeActionsEmail()} flow, personalised
	 * with Silingan specific context.
	 *
	 * <p>The context is written onto the Keycloak user as user attributes before the email is
	 * triggered, so the custom email theme can branch on {@code roleCode} / {@code invitationType}
	 * without any custom Keycloak SPI.
	 *
	 * @param keycloakUserId the Keycloak user ID
	 * @param requiredActions list of required actions (e.g., VERIFY_EMAIL, UPDATE_PASSWORD)
	 * @param context invitation context rendered by the email theme
	 */
	void sendInvitationEmail(String keycloakUserId, List<String> requiredActions,
		InvitationEmailContext context);

	/**
	 * Send invitation email via Keycloak using default invitation type (RESIDENT).
	 *
	 * Deprecated: Use sendRequiredActionsEmail(keycloakUserId, requiredActions, invitationType)
	 * to explicitly specify the invitation type.
	 *
	 * @param keycloakUserId the Keycloak user ID
	 * @param requiredActions list of required actions
	 */
	@Deprecated(since = "2.0", forRemoval = true)
	void sendRequiredActionsEmail(String keycloakUserId, List<String> requiredActions);

	String createInvitationUser(String email);

	/**
	 * Create (or reuse) a Keycloak user for an invitation, seeding the display name so the
	 * invitation email can greet the invitee by name.
	 *
	 * @param email invitee email, also used as the username
	 * @param firstName invitee first name, may be {@code null}
	 * @param lastName invitee last name, may be {@code null}
	 * @return the Keycloak user ID
	 */
	String createInvitationUser(String email, String firstName, String lastName);

	boolean isInvitationCompleted(String keycloakUserId, List<String> requiredActions);

}
