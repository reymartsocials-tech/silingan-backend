<#ftl output_format="plainText">
<#--
  Plain text counterpart of html/invitation-context.ftl.

  Kept as a separate file because Keycloak resolves email templates relative to
  the html/ and text/ directories independently, and text/ has no template.ftl.
-->

<#function attr name default="">
  <#if !user?? || !user.attributes??>
    <#return default>
  </#if>
  <#local raw = user.attributes[name]!"">
  <#if raw?is_sequence>
    <#if raw?size gt 0 && raw[0]?has_content>
      <#return raw[0]>
    </#if>
    <#return default>
  </#if>
  <#if raw?has_content>
    <#return raw?string>
  </#if>
  <#return default>
</#function>

<#function greetingName>
  <#if user?? && user.firstName?? && user.firstName?has_content>
    <#return user.firstName>
  </#if>
  <#if user?? && user.username?? && user.username?has_content>
    <#return user.username>
  </#if>
  <#return "there">
</#function>

<#function expiryText>
  <#if linkExpiration?? && linkExpirationFormatter??>
    <#return linkExpirationFormatter(linkExpiration)>
  </#if>
  <#return "a short while">
</#function>
