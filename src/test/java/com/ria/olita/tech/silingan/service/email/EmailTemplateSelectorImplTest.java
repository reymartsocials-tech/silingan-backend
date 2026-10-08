package com.ria.olita.tech.silingan.service.email;

import com.ria.olita.tech.silingan.entity.InvitationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EmailTemplateSelectorImplTest {

	private final EmailTemplateSelector templateSelector = new EmailTemplateSelectorImpl();

	@Test
	void testGetTemplateForResident() {
		String template = templateSelector.getTemplate(InvitationType.RESIDENT);
		assertEquals("community-invitation", template);
	}

	@Test
	void testGetTemplateForStaff() {
		String template = templateSelector.getTemplate(InvitationType.STAFF);
		assertEquals("staff-invitation", template);
	}

	@ParameterizedTest
	@EnumSource(InvitationType.class)
	void testGetTemplateForAllTypes(InvitationType type) {
		String template = templateSelector.getTemplate(type);
		assertNotNull(template, "Template name should not be null for " + type);
		assertEquals(type.getEmailTemplate(), template);
	}

	@Test
	void testGetDisplayNameForResident() {
		String displayName = templateSelector.getDisplayName(InvitationType.RESIDENT);
		assertEquals("Resident Invitation", displayName);
	}

	@Test
	void testGetDisplayNameForStaff() {
		String displayName = templateSelector.getDisplayName(InvitationType.STAFF);
		assertEquals("Staff Invitation", displayName);
	}

	@ParameterizedTest
	@EnumSource(InvitationType.class)
	void testGetDisplayNameForAllTypes(InvitationType type) {
		String displayName = templateSelector.getDisplayName(type);
		assertNotNull(displayName, "Display name should not be null for " + type);
		assertEquals(type.getDisplayName(), displayName);
	}

	@Test
	void testGetTemplateWithNullInvitationType() {
		// Should default to RESIDENT
		String template = templateSelector.getTemplate(null);
		assertEquals(InvitationType.RESIDENT.getEmailTemplate(), template);
	}

	@Test
	void testGetDisplayNameWithNullInvitationType() {
		// Should default to RESIDENT
		String displayName = templateSelector.getDisplayName(null);
		assertEquals(InvitationType.RESIDENT.getDisplayName(), displayName);
	}
}
