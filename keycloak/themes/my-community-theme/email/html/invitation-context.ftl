<#ftl output_format="HTML">
<#--
  Shared helpers for reading Silingan custom user attributes inside email templates.

  Keycloak exposes the user to email templates through ProfileBean, whose
  getAttributes() returns a Map<String, String> (single scalar value per key).
  Older/other Keycloak beans expose Map<String, List<String>> instead.

  Never index an attribute directly (e.g. user.attributes.roleCode[0]):
    - when the value is a String, [0] performs string slicing and yields "P"
    - when the attribute is absent, the template fails and no email is sent

  Always read attributes through attr() below, which handles both shapes and
  falls back to a default when the attribute is missing or blank.
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

<#--
  Greeting name: prefer the Keycloak first name, then the username, then a
  neutral fallback so invitations to brand-new users still read naturally.
-->
<#function greetingName>
  <#if user?? && user.firstName?? && user.firstName?has_content>
    <#return user.firstName>
  </#if>
  <#if user?? && user.username?? && user.username?has_content>
    <#return user.username>
  </#if>
  <#return "there">
</#function>

<#-- Human readable link expiration, e.g. "1 day". -->
<#function expiryText>
  <#if linkExpiration?? && linkExpirationFormatter??>
    <#return linkExpirationFormatter(linkExpiration)>
  </#if>
  <#return "a short while">
</#function>
