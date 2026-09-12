package com.ria.olita.tech.silingan.service;

import com.ria.olita.tech.silingan.dto.req.CreateUserRequest;
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

	void sendRequiredActionsEmail(String keycloakUserId, List<String> requiredActions);

	String createInvitationUser(String email);

	boolean isInvitationCompleted(String keycloakUserId, List<String> requiredActions);

}
