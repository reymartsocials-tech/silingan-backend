package com.ria.olita.tech.silingan.entity;

/**
 * Status of a user in the application.
 * 
 * PENDING: User created during invitation but not yet activated (first login not completed)
 * ACTIVE: User has completed first login and is fully activated
 * INACTIVE: User has been deactivated by admin
 */
public enum UserStatus {
	PENDING,
	ACTIVE,
	INACTIVE
}
