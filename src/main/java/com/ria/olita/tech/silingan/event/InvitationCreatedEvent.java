package com.ria.olita.tech.silingan.event;

import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEvent;
import com.ria.olita.tech.silingan.entity.InvitationType;
import lombok.Getter;

@Getter
public class InvitationCreatedEvent extends ApplicationEvent {

	private final UUID invitationId;
	private final String keycloakUserId;
	private final List<String> requiredActions;
	private final InvitationType invitationType;
	private final String email;
	private final UUID communityId;

	public InvitationCreatedEvent(Object source, UUID invitationId, String keycloakUserId, List<String> requiredActions,
		InvitationType invitationType, String email, UUID communityId) {
		super(source);
		this.invitationId = invitationId;
		this.keycloakUserId = keycloakUserId;
		this.requiredActions = requiredActions;
		this.invitationType = invitationType;
		this.email = email;
		this.communityId = communityId;
	}
}
