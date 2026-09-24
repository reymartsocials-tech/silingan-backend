package com.ria.olita.tech.silingan.service.email;

import com.ria.olita.tech.silingan.entity.InvitationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Default implementation of EmailTemplateSelector.
 *
 * Uses the InvitationType enum's built-in template mapping to determine which
 * Keycloak email template should be used for each invitation type.
 *
 * This implementation is thread-safe and requires no external configuration.
 * Adding new invitation types only requires updating the InvitationType enum.
 *
 * Example usage in KeycloakService:
 *   String templateName = templateSelector.getTemplate(InvitationType.STAFF);
 *   // Returns "staff-invitation"
 *   userResource.executeActionsEmail(clientId, redirectUri, lifespanSeconds,
 *     requiredActions, templateName);
 */
@Service
@Slf4j
public class EmailTemplateSelectorImpl implements EmailTemplateSelector {

	/**
	 * Get the template name from the InvitationType enum.
	 *
	 * This delegates to the enum's getEmailTemplate() method, keeping the
	 * template name mapping in one place (the enum).
	 *
	 * @param invitationType the invitation type
	 * @return the template name (e.g., "staff-invitation", "community-invitation")
	 */
	@Override
	public String getTemplate(InvitationType invitationType) {
		if (invitationType == null) {
			log.warn("InvitationType is null, defaulting to RESIDENT");
			return InvitationType.RESIDENT.getEmailTemplate();
		}
		String template = invitationType.getEmailTemplate();
		log.debug("Selected email template for {}: {}", invitationType, template);
		return template;
	}

	/**
	 * Get the display name from the InvitationType enum.
	 *
	 * @param invitationType the invitation type
	 * @return the display name (e.g., "Staff Invitation", "Resident Invitation")
	 */
	@Override
	public String getDisplayName(InvitationType invitationType) {
		if (invitationType == null) {
			log.warn("InvitationType is null, defaulting to RESIDENT");
			return InvitationType.RESIDENT.getDisplayName();
		}
		return invitationType.getDisplayName();
	}
}
