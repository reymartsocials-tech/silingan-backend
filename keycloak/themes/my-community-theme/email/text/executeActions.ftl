<#ftl output_format="plainText">
<#--
  Plain text router for executeActionsEmail(). Mirrors html/executeActions.ftl.
  There is no layout macro for text emails, so the partials are rendered directly.
-->
<#import "invitation-context.ftl" as ctx>
<#import "staff-invitation.ftl" as staff>
<#import "community-invitation.ftl" as community>

<#assign invitationType = ctx.attr("invitationType", "RESIDENT")?upper_case>
<#assign roleCode = ctx.attr("roleCode", "")?upper_case>
<#assign roleDisplayName = ctx.attr("roleDisplayName", "")>
<#assign communityName = ctx.attr("communityName", "your community")>
<#assign greeting = ctx.greetingName()>
<#assign expiration = ctx.expiryText()>
<#if invitationType == "STAFF">
<@staff.content roleCode=roleCode roleDisplayName=roleDisplayName communityName=communityName greeting=greeting expiration=expiration />
<#else>
<@community.content communityName=communityName greeting=greeting expiration=expiration />
</#if>
