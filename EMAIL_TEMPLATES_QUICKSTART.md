# Email Template Selection - Developer Quick Start

## Quick Reference

### Current Invitation Types

```java
// RESIDENT - For community member/admin invitations
InvitationType.RESIDENT
  → Template: community-invitation.ftl
  → Message: "You have been invited to join {community} as a community member."

// STAFF - For staff member invitations
InvitationType.STAFF
  → Template: staff-invitation.ftl
  → Message: "You have been invited to join {community} as a staff member."
```

---

## Usage Examples

### Example 1: Send Community Admin Invitation
```java
// In CommunityServiceImpl
String keycloakUserId = keycloakService.createInvitationUser(email);

keycloakService.updateUserAttributes(
    keycloakUserId,
    Map.of(
        "communityId", List.of(communityId.toString()),
        "communityName", List.of(community.getName())
    )
);

// Send invitation email using RESIDENT template
keycloakService.sendRequiredActionsEmail(
    keycloakUserId,
    List.of("VERIFY_EMAIL", "UPDATE_PROFILE", "UPDATE_PASSWORD"),
    InvitationType.RESIDENT
);
```

### Example 2: Send Staff Invitation
```java
// In StaffInvitationServiceImpl
String keycloakUserId = keycloakService.findUserIdByEmail(email)
    .orElseGet(() -> keycloakService.createInvitationUser(email));

keycloakService.updateUserAttributes(
    keycloakUserId,
    Map.of(
        "communityId", List.of(communityId.toString()),
        "communityName", List.of(community.getName())
    )
);

// Send invitation email using STAFF template
keycloakService.sendRequiredActionsEmail(
    keycloakUserId,
    List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"),
    InvitationType.STAFF
);
```

### Example 3: Add New Invitation Type (VENDOR)
```java
// Step 1: Update InvitationType enum
public enum InvitationType {
    RESIDENT("community-invitation", "Resident Invitation", "..."),
    STAFF("staff-invitation", "Staff Invitation", "..."),
    VENDOR("vendor-invitation", "Vendor Invitation", "You have been invited to partner with {communityName}.");
    // ... rest of enum stays the same
}

// Step 2: Create Keycloak templates
// keycloak/themes/my-community-theme/email/html/vendor-invitation.ftl
// keycloak/themes/my-community-theme/email/text/vendor-invitation.ftl

// Step 3: Use in your code
keycloakService.sendRequiredActionsEmail(
    keycloakUserId,
    requiredActions,
    InvitationType.VENDOR  // ← That's it!
);
```

---

## Testing Template Selection

### Unit Test Example
```java
@Test
void testStaffInvitationTemplateSelection() {
    EmailTemplateSelector selector = new EmailTemplateSelectorImpl();
    
    String template = selector.getTemplate(InvitationType.STAFF);
    assertEquals("staff-invitation", template);
    
    String displayName = selector.getDisplayName(InvitationType.STAFF);
    assertEquals("Staff Invitation", displayName);
}
```

### Mock Test Example
```java
@ExtendWith(MockitoExtension.class)
class StaffInvitationServiceTest {
    
    @Mock
    private KeycloakService keycloakService;
    
    @Test
    void testSendStaffInvitation() {
        // When staff invitation is created, it should use InvitationType.STAFF
        verify(keycloakService).sendRequiredActionsEmail(
            eq(keycloakUserId),
            eq(expectedActions),
            eq(InvitationType.STAFF)  // ← Verify correct type is used
        );
    }
}
```

---

## Email Template Variables

All templates have access to:

```freemarker
${user}                          ← Keycloak user object
${user.firstName}               ← User first name
${user.lastName}                ← User last name
${user.email}                   ← User email
${user.attributes}              ← Custom attributes map
${user.attributes.communityName}  ← Community name (can be string or list)
${link}                          ← Activation/action link
${kcSanitize(value)}            ← HTML escape function
```

### Accessing Community Name
```freemarker
<#assign communityName = "your community">
<#if user?? && user.attributes?? && user.attributes.communityName??>
  <#if user.attributes.communityName?is_sequence>
    <#if user.attributes.communityName?size gt 0>
      <#assign communityName = user.attributes.communityName[0]>
    </#if>
  <#else>
    <#assign communityName = user.attributes.communityName>
  </#if>
</#if>

<p>Welcome to ${kcSanitize(communityName)?no_esc}</p>
```

---

## Common Tasks

### Customize Staff Invitation Email
Edit: `keycloak/themes/my-community-theme/email/html/staff-invitation.ftl`

```html
<#ftl output_format="HTML">
<#import "template.ftl" as layout>

<#assign communityName = "your community">
<!-- Extract community name -->

<@layout.emailLayout>
  <p>Hello ${user.firstName},</p>
  
  <p>You have been invited to join <strong>${communityName}</strong> as a staff member.</p>
  
  <!-- Your custom content here -->
  
  <a href="${link}">Accept Invitation</a>
  
  <p>Regards,<br>The Silingan Team</p>
</@layout.emailLayout>
```

### Send Custom Invitation Type
```java
// Define new type in InvitationType enum
SECURITY_GUARD("security-guard-invitation", "Security Guard Invitation", "...")

// Send it
keycloakService.sendRequiredActionsEmail(
    keycloakUserId,
    requiredActions,
    InvitationType.SECURITY_GUARD
);
```

### Check Which Template Will Be Used
```java
EmailTemplateSelector selector = new EmailTemplateSelectorImpl();

String template = selector.getTemplate(InvitationType.STAFF);
// Returns: "staff-invitation"

String displayName = selector.getDisplayName(InvitationType.STAFF);
// Returns: "Staff Invitation"

// Log for debugging
log.info("Will use template: {} for invitation type: {}", template, displayName);
```

---

## Troubleshooting

### Q: Email template not found
**A:** Ensure template file exists:
- HTML: `keycloak/themes/my-community-theme/email/html/{template-name}.ftl`
- Text: `keycloak/themes/my-community-theme/email/text/{template-name}.ftl`
- Restart Keycloak after adding new templates

### Q: Wrong template being used
**A:** Check that InvitationType is being passed correctly:
```java
// ❌ Wrong - uses default (RESIDENT)
keycloakService.sendRequiredActionsEmail(userId, actions);

// ✅ Correct - explicitly specify type
keycloakService.sendRequiredActionsEmail(userId, actions, InvitationType.STAFF);
```

### Q: Community name not showing in email
**A:** Ensure Keycloak user has community attributes:
```java
keycloakService.updateUserAttributes(
    keycloakUserId,
    Map.of(
        "communityId", List.of(communityId.toString()),
        "communityName", List.of(community.getName())  // ← Required
    )
);
```

### Q: How to add new invitation type?
**A:** Four simple steps:
1. Add enum value to InvitationType
2. Create HTML template file
3. Create text template file
4. Use in code with `InvitationType.NEW_TYPE`

---

## Best Practices

### ✅ DO
- Always specify `InvitationType` when sending emails
- Set community attributes before sending invitation
- Use enum values (not strings) for template names
- Create both HTML and text templates for accessibility
- Test templates with real Keycloak instance

### ❌ DON'T
- Don't hardcode template names in services
- Don't send invitations without setting community name
- Don't use invalid InvitationType values
- Don't skip creating text template
- Don't forget to restart Keycloak after adding templates

---

## Related Files

- **Implementation Guide**: See `INVITATION_TEMPLATES.md`
- **Summary**: See `IMPLEMENTATION_COMPLETE.md`
- **Source Code**:
  - `entity/InvitationType.java` - Type definitions
  - `service/email/EmailTemplateSelector.java` - Strategy interface
  - `service/email/EmailTemplateSelectorImpl.java` - Implementation
  - `service/impl/KeycloakServiceImpl.java` - Email delivery
- **Templates**:
  - `keycloak/themes/my-community-theme/email/html/staff-invitation.ftl`
  - `keycloak/themes/my-community-theme/email/text/staff-invitation.ftl`
  - `keycloak/themes/my-community-theme/email/html/community-invitation.ftl`
  - `keycloak/themes/my-community-theme/email/text/community-invitation.ftl`
- **Tests**:
  - `service/email/EmailTemplateSelectorImplTest.java`
  - `service/impl/KeycloakServiceImplInvitationTypeTest.java`

---

## Summary

**To send an invitation:**

```java
// 1. Create or get user
String keycloakUserId = keycloakService.findUserIdByEmail(email)
    .orElseGet(() -> keycloakService.createInvitationUser(email));

// 2. Set community attributes
keycloakService.updateUserAttributes(keycloakUserId,
    Map.of("communityName", List.of(community.getName())));

// 3. Send with invitation type
keycloakService.sendRequiredActionsEmail(
    keycloakUserId,
    List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"),
    InvitationType.STAFF  // ← Specifies template
);
```

**To add new invitation type:**
1. Update `InvitationType` enum
2. Create template files
3. Use in code

That's it! 🎉
