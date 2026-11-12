package com.ria.olita.tech.silingan.entity;

/**
 * How the administrator intends to distribute an invitation.
 *
 * <p>Operational metadata only: it grants no trust, changes no authentication requirement and is
 * never evidence that the recipient is a resident.
 */
public enum InvitationDistributionChannel {
	EMAIL,
	VIBER_GC,
	DIRECT_MESSAGE,
	PRINTED,
	OTHER
}
