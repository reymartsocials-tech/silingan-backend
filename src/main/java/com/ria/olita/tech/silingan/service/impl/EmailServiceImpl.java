package com.ria.olita.tech.silingan.service.impl;

import org.springframework.stereotype.Service;

import com.ria.olita.tech.silingan.entity.Invitation;
import com.ria.olita.tech.silingan.service.EmailService;

import lombok.extern.slf4j.Slf4j;

/**
 * Email service implementation (stub for now).
 * In production, integrate with actual email provider (SendGrid, AWS SES, etc.).
 */
@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

	@Override
	public void sendInvitationEmail(Invitation invitation) {
		log.debug("Invitation email would be sent to {} for {}", 
			invitation.getEmail(), invitation.getType());
	}
}
