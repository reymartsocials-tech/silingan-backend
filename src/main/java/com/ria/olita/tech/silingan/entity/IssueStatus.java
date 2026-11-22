package com.ria.olita.tech.silingan.entity;

public enum IssueStatus {
	OPEN, IN_PROGRESS, AWAITING_RESIDENT_CONFIRMATION, RESOLVED, CLOSED, CANCELLED;

	/** A report still needs attention, i.e. it has not reached a terminal state. */
	public boolean isOpen() {
		return this == OPEN || this == IN_PROGRESS || this == AWAITING_RESIDENT_CONFIRMATION;
	}
}