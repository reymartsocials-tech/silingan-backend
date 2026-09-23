package com.ria.olita.tech.silingan.service.impl;

import com.ria.olita.tech.silingan.config.KeycloakProperties;
import com.ria.olita.tech.silingan.dto.req.CreateUserRequest;
import com.ria.olita.tech.silingan.entity.InvitationType;
import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.exception.ConflictException;
import com.ria.olita.tech.silingan.repository.UserCommunityRepository;
import com.ria.olita.tech.silingan.service.KeycloakService;
import com.ria.olita.tech.silingan.service.email.EmailTemplateSelector;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.BadRequestException;
import lombok.RequiredArgsConstructor;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KeycloakServiceImpl implements KeycloakService {
	private final UserCommunityRepository userCommunityRepository;
	private final EmailTemplateSelector emailTemplateSelector;
	private static final List<String> ADMIN_INVITATION_REQUIRED_ACTIONS = List.of(
		"VERIFY_EMAIL",
		"UPDATE_PROFILE",
		"UPDATE_PASSWORD"
	);

	private static final Logger log = LoggerFactory.getLogger(KeycloakServiceImpl.class);

	@Autowired
	private KeycloakProperties keycloakProperties;

	@Override
	public String createUser(CreateUserRequest request, UUID communityId) {
		log.info("Creating user in Keycloak: {}", request.username());
		log.debug("CreateUserRequest details - enabled: {}, emailVerified: {}, communityRole: {}", 
			request.enabled(), request.emailVerified(), request.communityRole());

		Keycloak keycloak = getKeycloakClient();
		RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
		UsersResource usersResource = realmResource.users();

		// Create user representation
		UserRepresentation user = new UserRepresentation();
		user.setUsername(request.username());
		if (request.email() != null && !request.email().isBlank()) {
			user.setEmail(request.email());
			user.setEmailVerified(request.emailVerified());
		}		user.setFirstName(request.firstName());
		user.setLastName(request.lastName());
		user.setEnabled(request.enabled());
		user.setEmailVerified(request.emailVerified());
		user.setRequiredActions(Collections.emptyList());

		if (request.password() != null && !request.password().isBlank()) {
			CredentialRepresentation credential = new CredentialRepresentation();
			credential.setType(CredentialRepresentation.PASSWORD);
			credential.setValue(request.password());
			credential.setTemporary(false);
			user.setCredentials(Collections.singletonList(credential));
		}

		// Create the user
		Response response = usersResource.create(user);
		log.info("Keycloak create user response status: {}", response.getStatus());

		if (response.getStatus() == 201) {
			String userId = extractUserId(response);
			log.info("User created successfully with ID: {}", userId);

			Map<String, List<String>> keycloakAttributes = new HashMap<>();
			keycloakAttributes.put("mobileNumber", List.of(request.mobileNumber()));
			keycloakAttributes.put("mobile_number_verified", List.of("true"));
			updateUserAttributes(userId, keycloakAttributes);

			if (request.communityRole() != null) {
				if (request.communityRole().equals(SilinganRealmRole.COMMUNITY_ADMIN) && userCommunityRepository.hasRoleInCommunity(communityId, request.communityRole())) {
					throw new ConflictException("Community already has a COMMUNITY_ADMIN assigned");
				}
				assignRealmRole(realmResource, userId, request.communityRole().name());
			} else {
				// Assign default RESIDENT role to the user
				assignRealmRole(realmResource, userId, SilinganRealmRole.RESIDENT.name());
			}
			return userId;
		} else if (response.getStatus() == 409) {
			log.error("User already exists: {}", request.username());
			throw new RuntimeException("User already exists: " + request.firstName());
		} else {
			String errorMessage = "Failed to create user. Status: " + response.getStatus();
			log.error(errorMessage);
			throw new RuntimeException(errorMessage);
		}
	}

	private Keycloak getKeycloakClient() {

		log.debug("Building Keycloak admin client for realm {} using clientId {}",
			keycloakProperties.getRealm(), keycloakProperties.getClientId());

		return KeycloakBuilder.builder()
			.serverUrl(keycloakProperties.getUrl())
			.realm(keycloakProperties.getRealm())
			.clientId(keycloakProperties.getClientId())
			.clientSecret(keycloakProperties.getClientSecret())
			.grantType(OAuth2Constants.CLIENT_CREDENTIALS)
			.build();
	}

	private String extractUserId(Response response) {
		String locationHeader = response.getHeaderString("Location");
		if (locationHeader != null) {
			return locationHeader.substring(locationHeader.lastIndexOf("/") + 1);
		}
		return null;
	}

	private void assignRealmRole(RealmResource realmResource, String userId, String roleName) {
		log.info("Assigning realm role {} to user {}", roleName, userId);

		try {
			// Get the role representation
			var rolesResource = realmResource.roles();
			RoleRepresentation role = rolesResource.get(roleName)
				.toRepresentation();

			if (role == null) {
				log.warn("Role {} not found, skipping assignment", roleName);
				return;
			}

			// Assign the role to the user
			var userResource = realmResource.users()
				.get(userId);
			userResource.roles()
				.realmLevel()
				.add(Collections.singletonList(role));

			log.info("Successfully assigned role {} to user {}", roleName, userId);

		} catch (Exception e) {
			log.error("Error assigning realm role: {}", e.getMessage(), e);
			// Don't throw - user creation should succeed even if role assignment fails
		}
	}

	public List<UserRepresentation> searchUsers(String username) {
		Keycloak keycloak = getKeycloakClient();
		RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
		UsersResource usersResource = realmResource.users();
		return usersResource.search(username, true);
	}

	@Override
	public void updateUserAttributes(String userId, Map<String, List<String>> attributes) {
		log.info("Updating attributes for user: {}", userId);

		Keycloak keycloak = getKeycloakClient();
		RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
		UsersResource usersResource = realmResource.users();

		// Get the user resource
		UserResource userResource = usersResource.get(userId);

		// Get current user representation
		UserRepresentation userRepresentation = userResource.toRepresentation();

		if (userRepresentation == null) {
			throw new RuntimeException("User not found with ID: " + userId);
		}

		// Get existing attributes
		Map<String, List<String>> existingAttributes = userRepresentation.getAttributes();
		if (existingAttributes == null) {
			existingAttributes = new HashMap<>();
		}

		// Merge attributes - new values override existing ones
		for (Map.Entry<String, List<String>> entry : attributes.entrySet()) {
			String key = entry.getKey();
			List<String> value = entry.getValue();
			existingAttributes.put(key, value);
			log.debug("Merging attribute: {} = {}", key, value);
		}

		// Update user with merged attributes
		userRepresentation.setAttributes(existingAttributes);
		userResource.update(userRepresentation);

		log.info("User attributes updated successfully for user: {}", userId);
	}

	/**
	 * Set the user type attribute on a Keycloak user.
	 * This tells the executeActions email template which invitation template to use.
	 * userType should be one of: "staff", "community", "resident"
	 *
	 * @param userResource the Keycloak user resource
	 * @param userType the user type (e.g., "staff", "community")
	 */
	private void setUserTypeAttribute(UserResource userResource, String userType) {
		try {
			UserRepresentation user = userResource.toRepresentation();
			if (user == null) {
				log.warn("Could not set userType attribute: user representation is null");
				return;
			}

			Map<String, List<String>> attributes = user.getAttributes();
			if (attributes == null) {
				attributes = new HashMap<>();
			}

			log.info("USER_TYPE_DEBUG: Setting userType attribute for user: {} = {}", user.getId(), userType);
			attributes.put("userType", List.of(userType));
			user.setAttributes(attributes);
			userResource.update(user);

			log.info("USER_TYPE_DEBUG: Successfully set userType attribute for user: {} = {}", user.getId(), userType);
		} catch (Exception e) {
			log.warn("Failed to set userType attribute: {}", e.getMessage(), e);
			// Don't fail the whole flow if setting attribute fails
		}
	}

	@Override
	public List<String> getRealmRoles(String keycloakUserId) {
		Keycloak keycloak = getKeycloakClient();
		RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
		UserResource userResource = realmResource.users().get(keycloakUserId);

		return userResource.roles()
			.realmLevel()
			.listAll()
			.stream()
			.map(RoleRepresentation::getName)
			.toList();
	}

	@Override
	public boolean isUserEnabled(String keycloakUserId) {
		Keycloak keycloak = getKeycloakClient();
		RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
		UserRepresentation userRepresentation = realmResource.users()
			.get(keycloakUserId)
			.toRepresentation();

		if (userRepresentation == null) {
			throw new RuntimeException("User not found with ID: " + keycloakUserId);
		}

		return Boolean.TRUE.equals(userRepresentation.isEnabled());
	}

	@Override
	public Optional<String> findUserIdByEmail(String email) {
		Keycloak keycloak = getKeycloakClient();
		RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
		UsersResource usersResource = realmResource.users();

		return usersResource.searchByEmail(email, true)
			.stream()
			.findFirst()
			.map(UserRepresentation::getId);
	}

	@Override
	public void assignRealmRole(String keycloakUserId, String roleName) {
		Keycloak keycloak = getKeycloakClient();
		RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
		assignRealmRole(realmResource, keycloakUserId, roleName);
	}

	@Override
	public void sendRequiredActionsEmail(String keycloakUserId, List<String> requiredActions) {
		sendRequiredActionsEmail(keycloakUserId, requiredActions, InvitationType.RESIDENT);
	}

	@Override
	public void sendRequiredActionsEmail(String keycloakUserId, List<String> requiredActions,
		InvitationType invitationType) {
		Keycloak keycloak = getKeycloakClient();
		RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
		UserResource userResource = realmResource.users().get(keycloakUserId);

		try {
			String templateName = emailTemplateSelector.getTemplate(invitationType);
			String displayName = emailTemplateSelector.getDisplayName(invitationType);
			
			// Set userType attribute to tell executeActions.ftl which template to use
			String userType = templateNameToUserType(templateName);
			setUserTypeAttribute(userResource, userType);
			
			String redirectClientId = keycloakProperties.getInvitationRedirectClientId();
			String redirectUri = keycloakProperties.getInvitationRedirectUri();
			Integer lifespanSeconds = keycloakProperties.getInvitationLifespanSeconds();

			if (isNotBlank(redirectClientId) && isNotBlank(redirectUri)) {
				log.info("Sending {} email with redirect to {} using client {} and userType '{}'",
					displayName, redirectUri, redirectClientId, userType);
				try {
					userResource.executeActionsEmail(redirectClientId, redirectUri, lifespanSeconds,
						requiredActions);
					return;
				} catch (BadRequestException badRequestException) {
					log.warn(
						"Keycloak rejected redirect-based actions email (clientId={}, redirectUri={}). Falling back to default actions email. " +
						"Ensure redirect URI is allowed for the client in Keycloak.",
						redirectClientId,
						redirectUri
					);
				}
			}

			log.info("Sending {} email without explicit redirect configuration using userType '{}'",
				displayName, userType);
			userResource.executeActionsEmail(requiredActions);
		} catch (Exception e) {
			String displayName = emailTemplateSelector.getDisplayName(invitationType);
			log.error("Error sending {} to user {}", displayName, keycloakUserId, e);
			throw new RuntimeException("Failed to send " + displayName, e);
		}
	}

	private String templateNameToUserType(String templateName) {
		if (templateName == null) {
			return "community";
		}
		return templateName.replace("-invitation", "");
	}

	private boolean isNotBlank(String value) {
		return value != null && !value.isBlank();
	}

	@Override
	public String createInvitationUser(String email) {
		Keycloak keycloak = getKeycloakClient();
		RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
		UsersResource usersResource = realmResource.users();

		UserRepresentation user = new UserRepresentation();
		user.setUsername(email.toLowerCase());
		user.setEmail(email.toLowerCase());
		user.setEnabled(true);
		user.setEmailVerified(false);
		user.setRequiredActions(ADMIN_INVITATION_REQUIRED_ACTIONS);

		Response response = usersResource.create(user);
		if (response.getStatus() != 201) {
			log.error("Failed to create invitation user {} in realm {}. HTTP status: {}",
				email,
				keycloakProperties.getRealm(),
				response.getStatus());

			if (response.getStatus() == 409) {
				return findUserIdByEmail(email)
					.orElseThrow(() -> new RuntimeException("Keycloak user already exists but cannot be resolved by email"));
			}
			throw new RuntimeException("Failed to create invitation user. Status: " + response.getStatus());
		}

		String userId = extractUserId(response);
		if (userId == null || userId.isBlank()) {
			throw new RuntimeException("Failed to resolve keycloak user id from create user response");
		}

		return userId;
	}

	@Override
	public boolean isInvitationCompleted(String keycloakUserId, List<String> requiredActions) {
		Keycloak keycloak = getKeycloakClient();
		RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
		UserRepresentation userRepresentation = realmResource.users()
			.get(keycloakUserId)
			.toRepresentation();

		if (userRepresentation == null || !Boolean.TRUE.equals(userRepresentation.isEmailVerified())) {
			log.debug("Invitation completion check: user={} emailVerified=false or user missing", keycloakUserId);
			return false;
		}

		List<String> pendingActions = Optional.ofNullable(userRepresentation.getRequiredActions()).orElse(List.of());
		log.debug("Invitation completion check: user={} pendingActions={} trackedActions={}", keycloakUserId, pendingActions, requiredActions);
		if (pendingActions.isEmpty()) {
			log.debug("Invitation completion check: user={} complete (no pending actions)", keycloakUserId);
			return true;
		}

		Set<String> trackedActions = new HashSet<>(Optional.ofNullable(requiredActions).orElse(List.of()));
		for (String action : pendingActions) {
			if (trackedActions.contains(action)) {
				log.debug("Invitation completion check: user={} incomplete due to pending tracked action={}", keycloakUserId, action);
				return false;
			}
		}

		log.debug("Invitation completion check: user={} complete (tracked actions cleared)", keycloakUserId);
		return true;
	}

	@Override
	public Map<String, List<String>> getUserAttributes(String keycloakUserId) {
		Keycloak keycloak = getKeycloakClient();
		RealmResource realmResource = keycloak.realm(keycloakProperties.getRealm());
		UserRepresentation userRepresentation = realmResource.users()
			.get(keycloakUserId)
			.toRepresentation();

		if (userRepresentation == null) {
			throw new RuntimeException("User not found with ID: " + keycloakUserId);
		}

		Map<String, List<String>> attributes = userRepresentation.getAttributes();
		return attributes != null ? attributes : Map.of();
	}
}
