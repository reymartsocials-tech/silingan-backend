package com.ria.olita.tech.silingan.entity.rbac;

import java.util.EnumSet;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;

@Getter
public class CommunityAccess {

	private final UUID communityId;
	private final Set<PermissionEnum> permissions;

	public CommunityAccess(UUID communityId, Collection<PermissionEnum> permissions) {
		this.communityId = communityId;
		this.permissions = EnumSet.noneOf(PermissionEnum.class);
		if (permissions != null) {
			this.permissions.addAll(permissions);
		}
	}

	public boolean hasPermission(Domain domain, Action action) {
		String permissionKey = domain.getValue() + ":" + action.value();
		return permissions.stream()
			.anyMatch(p -> p.getValue().equals(permissionKey));
	}

	public boolean canAccess(Domain domain) {
		return permissions.stream()
			.anyMatch(p -> p.getValue().startsWith(domain.getValue() + ":"));
	}
}
