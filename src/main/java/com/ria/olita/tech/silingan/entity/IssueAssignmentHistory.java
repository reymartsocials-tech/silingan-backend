package com.ria.olita.tech.silingan.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.ria.olita.tech.silingan.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Append-only record of every change to a report's assignment.
 *
 * <p>{@code reason} is internal and never shown to residents; {@code activityTitle} and
 * {@code activityMessage} are the resident-safe timeline text. A null {@code changedBy} denotes an
 * automatic (system) assignment.
 */
@Entity
@Table(name = "issue_assignment_histories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@SQLRestriction("deleted = false")
public class IssueAssignmentHistory extends BaseEntity {

	@Id
	@GeneratedValue
	@EqualsAndHashCode.Include
	@Column(columnDefinition = "UUID")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "issue_id", nullable = false)
	private Issue issue;

	@Column(name = "issue_id", insertable = false, updatable = false)
	private UUID issueId;

	@Enumerated(EnumType.STRING)
	@Column(name = "previous_destination", length = 30)
	private IssueRoutingDestination previousDestination;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "previous_staff_membership_id")
	private UserCommunity previousStaffMembership;

	@Enumerated(EnumType.STRING)
	@Column(name = "new_destination", nullable = false, length = 30)
	private IssueRoutingDestination newDestination;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "new_staff_membership_id")
	private UserCommunity newStaffMembership;

	@Enumerated(EnumType.STRING)
	@Column(name = "assignment_source", nullable = false, length = 30)
	private IssueAssignmentSource assignmentSource;

	@Column(name = "reason", length = 500)
	private String reason;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "changed_by")
	private User changedBy;

	@Column(name = "changed_at", nullable = false, columnDefinition = "TIMESTAMP")
	private LocalDateTime changedAt;

	@Column(name = "activity_title", nullable = false, length = 200)
	private String activityTitle;

	@Column(name = "activity_message", nullable = false, length = 500)
	private String activityMessage;
}
