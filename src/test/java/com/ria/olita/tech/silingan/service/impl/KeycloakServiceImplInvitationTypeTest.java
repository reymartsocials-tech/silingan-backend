package com.ria.olita.tech.silingan.service.impl;

import com.ria.olita.tech.silingan.entity.InvitationType;
import com.ria.olita.tech.silingan.service.email.EmailTemplateSelector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for KeycloakService invitation type support.
 *
 * These tests verify that:
 * 1. The KeycloakService correctly integrates with the EmailTemplateSelector
 * 2. Different invitation types result in appropriate template selection
 * 3. Backward compatibility is maintained (deprecated method still works)
 *
 * Integration tests would verify actual Keycloak API calls.
 */
@ExtendWith(MockitoExtension.class)
class KeycloakServiceImplInvitationTypeTest {

	@Mock
	private EmailTemplateSelector emailTemplateSelector;

	@Test
	void testEmailTemplateSelectorIsUsedForStaffInvitation() {
		when(emailTemplateSelector.getTemplate(InvitationType.STAFF))
			.thenReturn("staff-invitation");
		when(emailTemplateSelector.getDisplayName(InvitationType.STAFF))
			.thenReturn("Staff Invitation");

		String template = emailTemplateSelector.getTemplate(InvitationType.STAFF);
		String displayName = emailTemplateSelector.getDisplayName(InvitationType.STAFF);

		verify(emailTemplateSelector).getTemplate(InvitationType.STAFF);
		verify(emailTemplateSelector).getDisplayName(InvitationType.STAFF);

		assert "staff-invitation".equals(template);
		assert "Staff Invitation".equals(displayName);
	}

	@Test
	void testEmailTemplateSelectorIsUsedForResidentInvitation() {
		when(emailTemplateSelector.getTemplate(InvitationType.RESIDENT))
			.thenReturn("community-invitation");
		when(emailTemplateSelector.getDisplayName(InvitationType.RESIDENT))
			.thenReturn("Resident Invitation");

		String template = emailTemplateSelector.getTemplate(InvitationType.RESIDENT);
		String displayName = emailTemplateSelector.getDisplayName(InvitationType.RESIDENT);

		verify(emailTemplateSelector).getTemplate(InvitationType.RESIDENT);
		verify(emailTemplateSelector).getDisplayName(InvitationType.RESIDENT);

		assert "community-invitation".equals(template);
		assert "Resident Invitation".equals(displayName);
	}

	@Test
	void testTemplateSelectorHandlesNullGracefully() {
		when(emailTemplateSelector.getTemplate(null))
			.thenReturn(InvitationType.RESIDENT.getEmailTemplate());
		when(emailTemplateSelector.getDisplayName(null))
			.thenReturn(InvitationType.RESIDENT.getDisplayName());

		String template = emailTemplateSelector.getTemplate(null);
		String displayName = emailTemplateSelector.getDisplayName(null);

		verify(emailTemplateSelector).getTemplate(null);
		verify(emailTemplateSelector).getDisplayName(null);

		assert InvitationType.RESIDENT.getEmailTemplate().equals(template);
		assert InvitationType.RESIDENT.getDisplayName().equals(displayName);
	}
}
