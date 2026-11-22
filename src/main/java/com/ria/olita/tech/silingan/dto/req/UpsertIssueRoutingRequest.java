package com.ria.olita.tech.silingan.dto.req;

import java.util.UUID;

import com.ria.olita.tech.silingan.entity.IssueRoutingDestination;

import jakarta.validation.constraints.NotNull;

public record UpsertIssueRoutingRequest(
	@NotNull IssueRoutingDestination destinationType,
	UUID staffMembershipId
) {
}
