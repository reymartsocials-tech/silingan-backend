<#ftl output_format="plainText">
<#--
  Staff invitation content (plain text). Mirrors html/staff-invitation.ftl and
  branches on the same Silingan StaffRoleCode values.
-->

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

<#macro capabilities roleCode><#switch roleCode><#case "COMMUNITY_ADMIN">As Community Admin you will be able to:
- Manage the community profile, settings and branding
- Invite, assign and deactivate staff members
- Create and publish announcements to residents
- Maintain the community directory and emergency contacts
- Review and act on every resident report
- Oversee day-to-day community operations

<#break><#case "PMO_STAFF">As PMO Staff you will be able to:
- Triage and resolve resident reports and service requests
- Coordinate scheduled community activities and operations
- Publish operational announcements and advisories
- Work alongside security and maintenance teams
- Track resolution progress and close out completed work

<#break><#case "SECURITY_ADMIN">As Security Admin you will be able to:
- Monitor and respond to security incidents raised by residents
- Review visitor, gate pass and access activity
- Broadcast urgent safety advisories to the community
- Maintain emergency contacts and escalation paths
- Review security audit trails and incident history

<#break><#case "MAINTENANCE_ADMIN">As Maintenance Admin you will be able to:
- Receive and schedule maintenance and repair requests
- Assign jobs to maintenance crews and track completion
- Manage facility and amenity availability
- Notify residents about planned works and outages
- Keep maintenance history and asset records up to date

<#break><#case "READ_ONLY_STAFF">As Read-Only Staff you will be able to:
- View the community directory and staff contacts
- Follow resident reports and their current status
- Read announcements, advisories and operational updates
- Access reference dashboards for your assigned modules

Your access is view-only. If you need to act on a record, please ask a community
administrator to grant you an additional role.

<#break><#default>As a staff member you will be able to:
- Access the community staff directory
- View and respond to resident reports
- Collaborate with the rest of the staff team
- Manage your own profile and availability

</#switch></#macro>

<#macro content roleCode roleDisplayName communityName greeting expiration>
Hello ${greeting},

Welcome to Silingan!

You have been invited to join ${communityName} as ${label(roleCode, roleDisplayName)}.

<@capabilities roleCode=roleCode />
To get started, please activate your staff account using the link below:

${link}

For security reasons this invitation link expires in ${expiration}. If it expires,
ask a community administrator of ${communityName} to send a new invitation.

If you were not expecting this invitation you can safely ignore this email.
We look forward to helping you build a more connected and responsive community.

Warm regards,
The Silingan Team
</#macro>
