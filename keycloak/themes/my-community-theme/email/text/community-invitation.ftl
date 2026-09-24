<#ftl output_format="plainText">
<#--
  Resident / community invitation content (plain text).
  Fallback branch for any invitation that is not of type STAFF.
-->

<#macro content communityName greeting expiration>
Hello ${greeting},

Welcome to Silingan!

You have been invited to join ${communityName} on Silingan, the app that keeps
your community connected.

Once your account is active you will be able to:
- Receive announcements and advisories from your community
- Report issues and follow their progress
- Look up emergency contacts and community services
- Keep your household profile up to date

To get started, please activate your account using the link below:

${link}

For security reasons this invitation link expires in ${expiration}. If it expires,
please contact your Silingan administrator to request a new invitation.

If you were not expecting this invitation you can safely ignore this email.
We look forward to helping you build a more connected and responsive community.

Warm regards,
The Silingan Team
</#macro>
