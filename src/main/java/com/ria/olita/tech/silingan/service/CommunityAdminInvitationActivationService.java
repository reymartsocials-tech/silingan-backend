package com.ria.olita.tech.silingan.service;

import java.util.UUID;

public interface CommunityAdminInvitationActivationService {

	void activateIfCompleted(String keycloakUserId, UUID communityId);
}

