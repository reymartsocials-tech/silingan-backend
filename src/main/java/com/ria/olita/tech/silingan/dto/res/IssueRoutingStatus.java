package com.ria.olita.tech.silingan.dto.res;

/**
 * Effective state of a category route.
 * NOT_CONFIGURED and INVALID routes both send new reports to the PMO Review Queue.
 */
public enum IssueRoutingStatus {
	ACTIVE,
	NOT_CONFIGURED,
	INVALID
}
