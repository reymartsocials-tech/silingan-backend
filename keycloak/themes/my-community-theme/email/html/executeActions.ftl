<#ftl output_format="HTML">
<#--
  Router for Keycloak's executeActionsEmail() flow.

  This is the ONLY place that wraps content in layout.emailLayout. The
  invitation partials are macro libraries that emit body content only, so the
  layout is never nested.

  Routing inputs are plain Keycloak user attributes written by the Spring Boot
  application before executeActionsEmail() is called:
    invitationType    STAFF | RESIDENT
    roleCode          Silingan StaffRoleCode (application role, NOT a realm role)
    roleDisplayName   human readable label for roleCode
    communityName     community the invitation belongs to
    communityId       community UUID (kept for traceability, not rendered)
-->
<#import "template.ftl" as layout>
<#import "invitation-context.ftl" as ctx>
<#import "staff-invitation.ftl" as staff>
<#import "community-invitation.ftl" as community>

<#assign invitationType = ctx.attr("invitationType", "RESIDENT")?upper_case>
<#assign roleCode = ctx.attr("roleCode", "")?upper_case>
<#assign roleDisplayName = ctx.attr("roleDisplayName", "")>
<#assign communityName = ctx.attr("communityName", "your community")>
<#assign greeting = ctx.greetingName()>
<#assign expiration = ctx.expiryText()>

<@layout.emailLayout>
  <#if invitationType == "STAFF">
    <@staff.content
      roleCode=roleCode
      roleDisplayName=roleDisplayName
      communityName=communityName
      greeting=greeting
      expiration=expiration />
  <#else>
    <@community.content
      communityName=communityName
      greeting=greeting
      expiration=expiration />
  </#if>
</@layout.emailLayout>
