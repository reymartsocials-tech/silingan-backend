<#ftl output_format="HTML">
<#import "template.ftl" as layout>

<#-- Determine invitation type from userType attribute -->
<#assign invitationType = "community">
<#if user?? && user.attributes?? && user.attributes.userType??>
  <#if user.attributes.userType?is_sequence>
    <#if user.attributes.userType?size gt 0>
      <#assign invitationType = user.attributes.userType[0]>
    </#if>
  <#else>
    <#assign invitationType = user.attributes.userType>
  </#if>
</#if>

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

<@layout.emailLayout>
  <#-- Route to the appropriate invitation template based on userType -->
  <#if invitationType == "staff">
    <#include "staff-invitation.ftl">
  <#elseif invitationType == "resident">
    <#-- For now, use community template. Add resident-invitation.ftl if needed -->
    <#include "community-invitation.ftl">
  <#else>
    <#-- Default to community invitation template -->
    <#include "community-invitation.ftl">
  </#if>
</@layout.emailLayout>

