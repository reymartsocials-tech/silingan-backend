<#ftl output_format="HTML">
<#--
  Resident / community invitation content (HTML).

  Macro library only: emits body content, never the email layout. The layout is
  applied once by executeActions.ftl. This is the fallback branch for any
  invitation that is not of type STAFF.
-->

<#assign brandColor = "#0a2e11">

<#macro content communityName greeting expiration>
<p>Hello ${greeting},</p>

<p>Welcome to Silingan!</p>

<p>
  You have been invited to join <strong>${communityName}</strong> on Silingan,
  the app that keeps your community connected.
</p>

<p>Once your account is active you will be able to:</p>
<ul>
  <li>Receive announcements and advisories from your community</li>
  <li>Report issues and follow their progress</li>
  <li>Look up emergency contacts and community services</li>
  <li>Keep your household profile up to date</li>
</ul>

<p>To get started, please activate your account using the button below.</p>

<p>
  <a href="${link}" rel="nofollow"
     style="display:inline-block;padding:12px 20px;background:${brandColor};color:#ffffff;text-decoration:none;border-radius:6px;font-weight:600">
    Activate My Account
  </a>
</p>

<p>
  For security reasons this invitation link expires in <strong>${expiration}</strong>.
  If it expires, please contact your Silingan administrator to request a new
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
