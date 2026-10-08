package com.ria.olita.tech.silingan.event.listener;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import com.ria.olita.tech.silingan.event.InvitationCreatedEvent;
import com.ria.olita.tech.silingan.service.KeycloakService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvitationEmailListener {

	private final KeycloakService keycloakService;

	@EventListener
	@Async
	public void onInvitationCreated(InvitationCreatedEvent event) {
		try {
			log.debug("Async email send started for invitation - keycloakUserId: {}, email: {}, community: {}",
				event.getKeycloakUserId(), event.getEmail(), event.getCommunityId());

			keycloakService.sendRequiredActionsEmail(
				event.getKeycloakUserId(),
				event.getRequiredActions(),
				event.getInvitationType()
			);

			log.info("Invitation email sent successfully - email: {}, community: {}",
				event.getEmail(), event.getCommunityId());
		} catch (Exception e) {
			log.error(
				"Failed to send invitation email asynchronously - email: {}, community: {}, keycloakUserId: {}",
				event.getEmail(), event.getCommunityId(), event.getKeycloakUserId(), e);
		}
	}
}
