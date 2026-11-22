package com.ria.olita.tech.silingan.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "issue_resolution_decisions")
@Builder
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class IssueResolutionDecision extends BaseEntity {

	@Id
	@GeneratedValue
	@Column(columnDefinition = "UUID")
	private UUID id;

	@ManyToOne
	@JoinColumn(name = "issue_id", nullable = false)
	private Issue issue;

	@Column(name = "issue_id", insertable = false, updatable = false)
	private UUID issueId;

	@ManyToOne
	@JoinColumn(name = "resident_id", nullable = false)
	private User resident;

	@Column(name = "resident_id", insertable = false, updatable = false)
	private UUID residentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "decision", nullable = false, length = 30)
	private ResolutionDecision decision;

	@Column(name = "decided_at", nullable = false)
	private LocalDateTime decidedAt;
}
