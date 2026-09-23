<#ftl output_format="plainText">

<#-- Extract community name for use in templates -->
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

<#-- Route to appropriate template based on roleCode attribute -->
<#if user.attributes.roleCode[0] == "PMO_STAFF">
  <#include "staff-invitation.ftl">
<#elseif user.attributes.roleCode[0] == "TENANT">
  <#include "community-invitation.ftl">
<#elseif user.attributes.roleCode[0] == "COMMUNITY_ADMIN">
  <#include "community-invitation.ftl">
<#else>
  <#include "community-invitation.ftl">
</#if>

