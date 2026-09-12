package com.ria.olita.tech.silingan.security.permission;

import java.util.UUID;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.ria.olita.tech.silingan.entity.rbac.Action;
import com.ria.olita.tech.silingan.entity.rbac.CommunityAccess;
import com.ria.olita.tech.silingan.entity.rbac.Domain;
import com.ria.olita.tech.silingan.security.context.UserContext;
import com.ria.olita.tech.silingan.security.context.UserContextHolder;
import com.ria.olita.tech.silingan.security.scope.CommunityScopeGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionAspect {

	private final CommunityScopeGuard communityScopeGuard;

	@Before("@annotation(requiresPermission)")
	public void checkPermission(JoinPoint joinPoint, RequiresPermission requiresPermission) {
		Domain domain = requiresPermission.domain();
		Action action = requiresPermission.action();

		if (UserContextHolder.isPlatformAdmin()) {
			log.debug("Platform admin access granted for {}:{}", domain, action);
			return;
		}

		UserContext user = UserContextHolder.get();
		if (user == null) {
			throw new AccessDeniedException("No authenticated user context");
		}

		UUID communityId = extractCommunityId(joinPoint, user);

		// A COMMUNITY_ADMIN realm role is not bound to a community; it only grants a bypass for
		// communities the caller actually belongs to.
		if (UserContextHolder.isCommunityAdmin() && communityScopeGuard.hasAccess(communityId)) {
			log.debug("Community admin access granted for {}:{}", domain, action);
			return;
		}

		if (UserContextHolder.isStaff()) {
			CommunityAccess access = user.getAccess(communityId);
			if (access != null && access.hasPermission(domain, action)) {
				log.debug("Staff permission granted for {}:{}", domain, action);
				return;
			}
		}

		throw new AccessDeniedException("Insufficient permissions for " + domain.getValue() + ":" + action.value());
	}

	private UUID extractCommunityId(JoinPoint joinPoint, UserContext user) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		String[] paramNames = signature.getParameterNames();
		Object[] args = joinPoint.getArgs();

		for (int i = 0; i < paramNames.length; i++) {
			if ("communityId".equals(paramNames[i]) && args[i] instanceof UUID uuid) {
				return uuid;
			}
		}

		for (Object arg : args) {
			if (arg != null) {
				try {
					var method = arg.getClass().getMethod("communityId");
					Object result = method.invoke(arg);
					if (result instanceof UUID uuid) {
						return uuid;
					}
				} catch (Exception ignored) {
				}
			}
		}

		return user.communityId() != null ? UUID.fromString(user.communityId()) : null;
	}
}
