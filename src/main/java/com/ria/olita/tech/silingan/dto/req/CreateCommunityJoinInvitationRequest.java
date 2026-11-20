package com.ria.olita.tech.silingan.dto.req;

import com.ria.olita.tech.silingan.entity.InvitationDistributionChannel;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Request to create a community join invitation.
 *
 * <p>The community comes from the path and the creator from the authenticated principal, so neither
 * is accepted here. Token, status, usage and revocation data are server-owned.
 */
@Builder
@Schema(description = "Create a QR/link join invitation for a community")
public record CreateCommunityJoinInvitationRequest(

	@Size(max = 150, message = "Label must not exceed 150 characters")
	@Schema(description = "Administrator-facing label for this rollout", example = "Tower A - November resident onboarding")
	String label,

	@Schema(description = "How the administrator intends to share the invitation", example = "VIBER_GC")
	InvitationDistributionChannel distributionChannel,

	@Min(value = 1, message = "Expiry must be at least 1 hour")
	@Schema(description = "Lifetime in hours; defaults to the configured value when omitted", example = "168")
	Integer expiresInHours,

	@Min(value = 1, message = "Maximum uses must be at least 1")
	@Schema(description = "Maximum number of successful registrations; defaults to the configured value when omitted", example = "100")
	Integer maxUses
) {
}
