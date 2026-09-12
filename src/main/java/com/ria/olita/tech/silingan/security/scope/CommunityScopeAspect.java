package com.ria.olita.tech.silingan.security.scope;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.UUID;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import lombok.RequiredArgsConstructor;

/**
 * Applies {@link CommunityScopeGuard} to every controller method that accepts a {@code communityId}
 * path variable.
 *
 * <p>The check is opt-out ({@link SkipCommunityScopeCheck}) rather than opt-in so that a newly added
 * community-scoped endpoint is protected by default instead of silently inheriting the
 * cross-community hole.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class CommunityScopeAspect {

	private static final String COMMUNITY_ID_PARAM = "communityId";

	private final CommunityScopeGuard communityScopeGuard;

	@Before("within(com.ria.olita.tech.silingan.rest..*)")
	public void enforceCommunityScope(JoinPoint joinPoint) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Method method = signature.getMethod();

		if (method.isAnnotationPresent(SkipCommunityScopeCheck.class)
			|| method.getDeclaringClass().isAnnotationPresent(SkipCommunityScopeCheck.class)) {
			return;
		}

		UUID communityId = extractCommunityId(method, joinPoint.getArgs());
		if (communityId != null) {
			communityScopeGuard.assertAccess(communityId);
		}
	}

	private UUID extractCommunityId(Method method, Object[] args) {
		Parameter[] parameters = method.getParameters();

		for (int i = 0; i < parameters.length && i < args.length; i++) {
			if (!(args[i] instanceof UUID uuid)) {
				continue;
			}

			PathVariable pathVariable = parameters[i].getAnnotation(PathVariable.class);
			if (pathVariable == null) {
				continue;
			}

			String boundName = !pathVariable.name().isBlank()
				? pathVariable.name()
				: (!pathVariable.value().isBlank() ? pathVariable.value() : parameters[i].getName());

			if (COMMUNITY_ID_PARAM.equals(boundName)) {
				return uuid;
			}
		}

		return null;
	}
}
