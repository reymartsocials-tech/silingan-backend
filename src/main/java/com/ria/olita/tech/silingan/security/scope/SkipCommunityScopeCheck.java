package com.ria.olita.tech.silingan.security.scope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opt-out marker for endpoints that expose a {@code communityId} parameter which is not a tenant
 * boundary (for example platform-level provisioning). Use sparingly and document the reason.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SkipCommunityScopeCheck {

	String reason();
}
