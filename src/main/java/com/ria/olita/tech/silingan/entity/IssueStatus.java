package com.ria.olita.tech.silingan.entity;

public enum IssueStatus {
	OPEN, IN_PROGRESS, RESOLVED, CLOSED, CANCELLED;

	/** A report still needs attention, i.e. it has not reached a terminal state. */
	public boolean isOpen() {
		return this == OPEN || this == IN_PROGRESS;
	}
}