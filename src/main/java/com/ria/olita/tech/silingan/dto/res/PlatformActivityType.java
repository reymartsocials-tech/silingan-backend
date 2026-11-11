package com.ria.olita.tech.silingan.dto.res;

import lombok.Getter;

@Getter
public enum PlatformActivityType {
	COMMUNITY_CREATED("New community created"),
	COMMUNITY_ADMIN_INVITED("Administrator invited"),
	INVITATION_ACCEPTED("Administrator invitation accepted"),
	INVITATION_REVOKED("Administrator invitation revoked");

	private final String title;

	PlatformActivityType(String title) {
		this.title = title;
	}
}
