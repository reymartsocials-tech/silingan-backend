package com.ria.olita.tech.silingan.entity.rbac;

public enum Action {
	VIEW,
	MANAGE;

	public String value() {
		return name().toLowerCase();
	}

	public boolean canManage() {
		return this == MANAGE;
	}
}
