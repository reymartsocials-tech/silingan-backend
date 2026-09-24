<#ftl output_format="HTML">
<#--
  Staff invitation content (HTML).

  Macro library only: emits body content, never the email layout. The layout is
  applied once by executeActions.ftl.

  Content is personalised with the Silingan application role (StaffRoleCode).
  These roles live only in the Silingan database; they are NOT Keycloak realm
  roles. They reach this template as the "roleCode" user attribute.

  Supported roleCode values (com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode):
    COMMUNITY_ADMIN, PMO_STAFF, SECURITY_ADMIN, MAINTENANCE_ADMIN, READ_ONLY_STAFF
-->

<#assign brandColor = "#0a2e11">

<#-- Fallback label when the app did not send roleDisplayName. -->
<#function label roleCode roleDisplayName>
  <#if roleDisplayName?has_content>
    <#return roleDisplayName>
  </#if>
  <#switch roleCode>
    <#case "COMMUNITY_ADMIN"><#return "Community Admin">
    <#case "PMO_STAFF"><#return "PMO Staff">
    <#case "SECURITY_ADMIN"><#return "Security Admin">
    <#case "MAINTENANCE_ADMIN"><#return "Maintenance Admin">
    <#case "READ_ONLY_STAFF"><#return "Read-Only Staff">
    <#default><#return "Staff Member">
  </#switch>
</#function>

<#-- Role specific capability list. -->
<#macro capabilities roleCode>
  <#switch roleCode>
    <#case "COMMUNITY_ADMIN">
      <p>As <strong>Community Admin</strong> you will be able to:</p>
      <ul>
        <li>Manage the community profile, settings and branding</li>
        <li>Invite, assign and deactivate staff members</li>
        <li>Create and publish announcements to residents</li>
        <li>Maintain the community directory and emergency contacts</li>
        <li>Review and act on every resident report</li>
        <li>Oversee day-to-day community operations</li>
      </ul>
      <#break>
    <#case "PMO_STAFF">
      <p>As <strong>PMO Staff</strong> you will be able to:</p>
      <ul>
        <li>Triage and resolve resident reports and service requests</li>
        <li>Coordinate scheduled community activities and operations</li>
        <li>Publish operational announcements and advisories</li>
        <li>Work alongside security and maintenance teams</li>
        <li>Track resolution progress and close out completed work</li>
      </ul>
      <#break>
    <#case "SECURITY_ADMIN">
      <p>As <strong>Security Admin</strong> you will be able to:</p>
      <ul>
        <li>Monitor and respond to security incidents raised by residents</li>
        <li>Review visitor, gate pass and access activity</li>
        <li>Broadcast urgent safety advisories to the community</li>
        <li>Maintain emergency contacts and escalation paths</li>
        <li>Review security audit trails and incident history</li>
      </ul>
      <#break>
    <#case "MAINTENANCE_ADMIN">
      <p>As <strong>Maintenance Admin</strong> you will be able to:</p>
      <ul>
        <li>Receive and schedule maintenance and repair requests</li>
        <li>Assign jobs to maintenance crews and track completion</li>
        <li>Manage facility and amenity availability</li>
        <li>Notify residents about planned works and outages</li>
        <li>Keep maintenance history and asset records up to date</li>
      </ul>
      <#break>
    <#case "READ_ONLY_STAFF">
      <p>As <strong>Read-Only Staff</strong> you will be able to:</p>
      <ul>
        <li>View the community directory and staff contacts</li>
        <li>Follow resident reports and their current status</li>
        <li>Read announcements, advisories and operational updates</li>
        <li>Access reference dashboards for your assigned modules</li>
      </ul>
      <p>
        Your access is <strong>view-only</strong>. If you need to act on a
        record, please ask a community administrator to grant you an
        additional role.
      </p>
      <#break>
    <#default>
      <p>As a <strong>staff member</strong> you will be able to:</p>
      <ul>
        <li>Access the community staff directory</li>
        <li>View and respond to resident reports</li>
        <li>Collaborate with the rest of the staff team</li>
        <li>Manage your own profile and availability</li>
      </ul>
  </#switch>
</#macro>

<#macro content roleCode roleDisplayName communityName greeting expiration>
<p>Hello ${greeting},</p>

<p>Welcome to Silingan!</p>

<p>
  You have been invited to join <strong>${communityName}</strong> as
  <strong>${label(roleCode, roleDisplayName)}</strong>.
</p>

<@capabilities roleCode=roleCode />

<p>To get started, please activate your staff account using the button below.</p>

<p>
  <a href="${link}" rel="nofollow"
     style="display:inline-block;padding:12px 20px;background:${brandColor};color:#ffffff;text-decoration:none;border-radius:6px;font-weight:600">
    Activate My Staff Account
  </a>
</p>

<p>
  For security reasons this invitation link expires in <strong>${expiration}</strong>.
  If it expires, ask a community administrator of ${communityName} to send a new
  invitation.
</p>

<p>
  If you were not expecting this invitation you can safely ignore this email.<br/>
  We look forward to helping you build a more connected and responsive community.
</p>

<p>
  Warm regards,<br/>
  <strong>The Silingan Team</strong>
</p>
</#macro>
