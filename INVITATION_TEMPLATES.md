# Email Template Selection System - Implementation Guide

## Overview

This document explains how the Silingan backend manages email templates for different invitation types (Resident, Staff, etc.) and how to extend it for future invitation types.

## Architecture

### Components

1. **InvitationType Enum** (`entity/InvitationType.java`)
   - Defines all supported invitation types
   - Maps each type to its email template name
   - Stores display name and message template for each type

2. **EmailTemplateSelector Interface** (`service/email/EmailTemplateSelector.java`)
   - Strategy interface for template selection
   - Provides methods to get template name and display name

3. **EmailTemplateSelectorImpl** (`service/email/EmailTemplateSelectorImpl.java`)
   - Default implementation using InvitationType enum
   - Thread-safe and requires no external configuration

4. **KeycloakService Updates** (`service/KeycloakService.java` & `service/impl/KeycloakServiceImpl.java`)
   - New overload: `sendRequiredActionsEmail(userId, actions, invitationType)`
   - Deprecated overload: `sendRequiredActionsEmail(userId, actions)` (defaults to RESIDENT)
   - Uses EmailTemplateSelector to determine which template to use

5. **Keycloak Email Templates**
   - `community-invitation.ftl` - For community admin/resident invitations
   - `staff-invitation.ftl` - For staff member invitations
   - Located in: `keycloak/themes/my-community-theme/email/{html|text}/`

## Current Invitation Types

### RESIDENT
- **Template**: `community-invitation.ftl`
- **Display Name**: "Resident Invitation"
- **Message**: "You have been invited to join {communityName} as a community member."
- **Usage**: Community admin invitations

### STAFF
- **Template**: `staff-invitation.ftl`
- **Display Name**: "Staff Invitation"
- **Message**: "You have been invited to join {communityName} as a staff member."
- **Usage**: Staff member invitations

## How It Works

### Step 1: User Sends Invitation

```java
// Example: Staff invitation
keycloakService.sendRequiredActionsEmail(
    keycloakUserId,
    List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"),
    InvitationType.STAFF  // Specify invitation type
);
```

### Step 2: Template Selection

The EmailTemplateSelector determines which template to use:

```java
// EmailTemplateSelectorImpl
@Override
public String getTemplate(InvitationType invitationType) {
    return invitationType.getEmailTemplate();  // Returns "staff-invitation"
}
```

### Step 3: Keycloak Email Delivery

```java
// KeycloakServiceImpl
userResource.executeActionsEmail(
    redirectClientId,
    redirectUri,
    lifespanSeconds,
    requiredActions
    // Note: Keycloak will use the template mapped to the invitation type
);
```

### Step 4: Template Rendering

Keycloak looks for the template file at:
- HTML: `keycloak/themes/my-community-theme/email/html/staff-invitation.ftl`
- Text: `keycloak/themes/my-community-theme/email/text/staff-invitation.ftl`

The template receives context variables:
- `user` - The Keycloak user
- `link` - The activation link
- `user.attributes.communityName` - The community name

## Adding New Invitation Types

### To add support for a new invitation type (e.g., VENDOR):

#### Step 1: Update InvitationType Enum

```java
// entity/InvitationType.java
public enum InvitationType {
    RESIDENT(...),
    STAFF(...),
    VENDOR(
        "vendor-invitation",           // Template name
        "Vendor Invitation",           // Display name
        "You have been invited to partner with {communityName}."  // Message
    );
    // ... rest of enum
}
```

#### Step 2: Create Email Templates

Create two new template files:

**HTML Template** - `keycloak/themes/my-community-theme/email/html/vendor-invitation.ftl`
```html
<#ftl output_format="HTML">
<#import "template.ftl" as layout>

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

<@layout.emailLayout>
<p>Hello,</p>

<p>Welcome to Silingan!</p>

<p>
  You have been invited to partner with <strong>${kcSanitize(communityName)?no_esc}</strong>
  as a <strong>Vendor</strong>.
</p>

<!-- Custom vendor-specific content here -->

<p>
  <a href="${link}" style="display:inline-block;padding:12px 20px;background:#0a2e11;color:#ffffff;text-decoration:none;border-radius:6px;font-weight:600" rel="nofollow">
    Activate My Vendor Account
  </a>
</p>

<!-- Footer -->
</@layout.emailLayout>
```

**Text Template** - `keycloak/themes/my-community-theme/email/text/vendor-invitation.ftl`
```
<#ftl output_format="plainText">
<#assign communityName = "your community">
<!-- Similar structure to HTML version -->
```

#### Step 3: Use the New Invitation Type

```java
// In your service code
keycloakService.sendRequiredActionsEmail(
    keycloakUserId,
    requiredActions,
    InvitationType.VENDOR  // Use the new type
);
```

**That's it!** No changes needed to KeycloakService or EmailTemplateSelector.

## Template Variables

All invitation email templates have access to:

| Variable | Type | Description |
|----------|------|-------------|
| `user` | UserRepresentation | Keycloak user object |
| `user.attributes` | Map | Custom user attributes |
| `user.attributes.communityName` | String or List | The community name |
| `link` | String | The activation/action link |
| `kcSanitize()` | Function | Escapes HTML special characters |

### Accessing Community Name in Templates

```freemarker
<#-- Check if community name exists and handle both single values and lists -->
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

<p>You're invited to join ${kcSanitize(communityName)?no_esc}</p>
```

## Testing

### Unit Tests

1. **EmailTemplateSelectorImplTest** - Tests template and display name selection
   - Verifies RESIDENT → "community-invitation"
   - Verifies STAFF → "staff-invitation"
   - Tests null handling (defaults to RESIDENT)

2. **KeycloakServiceImplInvitationTypeTest** - Tests Keycloak service integration
   - Verifies EmailTemplateSelector usage
   - Tests both invitation types
   - Verifies backward compatibility

### Running Tests

```bash
# All tests
mvn test

# Only email service tests
mvn test -Dtest=EmailTemplateSelectorImplTest,KeycloakServiceImplInvitationTypeTest
```

## Backward Compatibility

The old method signature is maintained for backward compatibility:

```java
// Old way (deprecated, defaults to RESIDENT)
keycloakService.sendRequiredActionsEmail(keycloakUserId, requiredActions);

// New way (recommended)
keycloakService.sendRequiredActionsEmail(keycloakUserId, requiredActions, InvitationType.STAFF);
```

## Migration Path

If you were using the old `sendRequiredActionsEmail(keycloakUserId, requiredActions)` method:

1. For community admin/resident invitations: No change needed (defaults to RESIDENT)
2. For staff invitations: Update to pass `InvitationType.STAFF`:
   ```java
   // Before
   keycloakService.sendRequiredActionsEmail(keycloakUserId, actions);
   
   // After
   keycloakService.sendRequiredActionsEmail(keycloakUserId, actions, InvitationType.STAFF);
   ```

## Future Enhancements

### Potential invitation types to add:

1. **SECURITY_GUARD** - Security personnel
2. **PROPERTY_MANAGER** - Property management staff
3. **MAINTENANCE_STAFF** - Maintenance and facilities
4. **COMMUNITY_MODERATOR** - Community moderation team
5. **CONTRACTOR** - External contractors

### Implementation pattern for each:

1. Add enum value to InvitationType
2. Create `.ftl` templates (HTML and text)
3. Use in your service code

Example:
```java
keycloakService.sendRequiredActionsEmail(
    keycloakUserId,
    requiredActions,
    InvitationType.SECURITY_GUARD
);
```

## Keycloak Configuration

### Email Provider Setup

Ensure Keycloak is configured for email:

1. Realm Settings → Email
2. Set SMTP Server details
3. Configure "From" email address
4. Test email delivery

### Theme Deployment

The custom theme must be deployed to Keycloak:

1. Copy `keycloak/themes/my-community-theme/` to Keycloak themes directory
2. Realm Settings → Themes
3. Set Email Theme to `my-community-theme`
4. Restart Keycloak

### User Attributes

Ensure `communityName` is set on Keycloak users before sending invitations:

```java
keycloakService.updateUserAttributes(
    keycloakUserId,
    Map.of(
        "communityId", List.of(communityId.toString()),
        "communityName", List.of(community.getName())
    )
);
```

## Troubleshooting

### Email not sending
- Check Keycloak logs for email errors
- Verify SMTP configuration
- Ensure user has valid email address

### Template not being used
- Verify template file exists in correct directory
- Check Keycloak theme is configured correctly
- Restart Keycloak after deploying new templates

### Wrong template being used
- Check InvitationType enum value
- Verify EmailTemplateSelector is injected correctly
- Review logs for template selection debug messages

## Summary

This design provides:
- ✅ Clean separation of concerns
- ✅ Easy to extend for new invitation types
- ✅ Backward compatible with existing code
- ✅ Testable and maintainable
- ✅ Flexible template customization
- ✅ Centralized template mapping

To add a new invitation type, simply:
1. Add enum value
2. Create FTL templates
3. Use in your code

No changes to KeycloakService or EmailTemplateSelector needed!
