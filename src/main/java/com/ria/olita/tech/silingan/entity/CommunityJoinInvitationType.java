package com.ria.olita.tech.silingan.entity;

/**
 * Kind of community join invitation.
 *
 * <p>Only {@link #COMMUNITY_BATCH} is supported for the MVP: one reusable invitation shared through
 * a controlled resident channel. The column is an enum string so a future per-resident invitation
 * type can be added without changing existing rows or behaviour.
 */
public enum CommunityJoinInvitationType {
	COMMUNITY_BATCH
}
