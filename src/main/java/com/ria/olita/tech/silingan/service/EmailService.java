package com.ria.olita.tech.silingan.service;

import com.ria.olita.tech.silingan.entity.StaffInvitation;

/**
 * Service for sending emails to staff members.
 * Implementations should handle async email delivery.
 */
public interface EmailService {

	/**
	 * Send staff invitation email.
	 * Should be called asynchronously.
	 *
	 * @param invitation the staff invitation
	 * @param invitationLink the link to activate the invitation
	 */
	void sendStaffInvitationEmail(StaffInvitation invitation, String invitationLink);
}
