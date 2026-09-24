package com.ria.olita.tech.silingan.entity;

/**
 * Enumeration of invitation types for different user roles and workflows.
 * Each type has a corresponding email template and messaging strategy.
 *
 * Future invitation types can be added without breaking existing code:
 * - VENDOR: For inviting vendors/service providers
 * - SECURITY_GUARD: For inviting security personnel
 * - PROPERTY_MANAGER: For inviting property management staff
 *
 * Usage:
 * - Template selection: invitationType.getEmailTemplate()
 * - Message context: Used to set user attributes in Keycloak
 * - Permission validation: Can be extended to control what roles are available for each type
 */
public enum InvitationType {
	RESIDENT(
		"community-invitation",
		"Resident Invitation",
		"You have been invited to join {communityName} as a community member."
	),
	STAFF(
		"staff-invitation",
		"Staff Invitation",
		"You have been invited to join {communityName} as a staff member."
	);

	private final String emailTemplate;
	private final String displayName;
	private final String messageTemplate;

	InvitationType(String emailTemplate, String displayName, String messageTemplate) {
		this.emailTemplate = emailTemplate;
		this.displayName = displayName;
		this.messageTemplate = messageTemplate;
	}

	public String getEmailTemplate() {
		return emailTemplate;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getMessageTemplate() {
		return messageTemplate;
	}
}
