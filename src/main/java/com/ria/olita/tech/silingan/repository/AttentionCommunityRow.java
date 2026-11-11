package com.ria.olita.tech.silingan.repository;

import java.util.UUID;

import com.ria.olita.tech.silingan.entity.CommunityStatus;

public record AttentionCommunityRow(
	UUID communityId,
	String name,
	CommunityStatus status,
	long residentCount,
	long adminCount,
	long pendingAdminInvitations
) {
}
