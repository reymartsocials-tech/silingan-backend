package com.ria.olita.tech.silingan.service;

import com.ria.olita.tech.silingan.entity.Invitation;

/**
 * Service for sending emails to users.
 * Implementations should handle async email delivery.
 */
public interface EmailService {

	/**
	 * Send invitation email for staff or admin users.
	 * Should be called asynchronously.
	 *
	 * @param invitation the invitation (staff or admin)
	 */
	void sendInvitationEmail(Invitation invitation);
}
