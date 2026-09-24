package com.ria.olita.tech.silingan.service.email;

import com.ria.olita.tech.silingan.entity.InvitationType;

/**
 * Service for selecting email templates based on invitation type.
 *
 * This service encapsulates template selection logic and makes it easy to add new
 * invitation types without modifying the Keycloak service or email sending logic.
 *
 * The mapping between InvitationType and email template names (e.g., "staff-invitation")
 * is centralized here, making it maintainable and extensible.
 *
 * Template files are located in:
 * - keycloak/themes/my-community-theme/email/html/{template}.ftl
 * - keycloak/themes/my-community-theme/email/text/{template}.ftl
 *
 * To add a new invitation type:
 * 1. Add a new enum value to InvitationType (e.g., VENDOR, SECURITY_GUARD)
 * 2. Create corresponding template files (vendor-invitation.ftl, etc.)
 * 3. No changes needed here - the mapping is automatic via InvitationType.getEmailTemplate()
 */
public interface EmailTemplateSelector {

	/**
	 * Get the email template name for a given invitation type.
	 *
	 * The returned template name is used by Keycloak to locate the FTL template file:
	 * - HTML: keycloak/themes/my-community-theme/email/html/{templateName}.ftl
	 * - Text: keycloak/themes/my-community-theme/email/text/{templateName}.ftl
	 *
	 * @param invitationType the type of invitation (RESIDENT, STAFF, etc.)
	 * @return the template name (e.g., "staff-invitation", "community-invitation")
	 */
	String getTemplate(InvitationType invitationType);

	/**
	 * Get the display name for a given invitation type.
	 *
	 * Useful for logging, user messages, and UI displays.
	 *
	 * @param invitationType the type of invitation
	 * @return the display name (e.g., "Staff Invitation", "Resident Invitation")
	 */
	String getDisplayName(InvitationType invitationType);
}
