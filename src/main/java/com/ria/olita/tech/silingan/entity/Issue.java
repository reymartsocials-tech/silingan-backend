package com.ria.olita.tech.silingan.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.SQLRestriction;

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
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "issues")
@Builder
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@SQLRestriction("deleted = false")
@FilterDef(
	name = "communityFilter",
	parameters = @ParamDef(name = "communityId", type = UUID.class)
)
@Filter(
	name = "communityFilter",
	condition = "community_id = :communityId"
)
public class Issue extends BaseEntity {

	@Id
	@GeneratedValue
	@Column(columnDefinition = "UUID")
	private UUID id;

	@ManyToOne
	@JoinColumn(name = "community_id", columnDefinition = "UUID NOT NULL")
	private Community community;

	@Column(name = "community_id", insertable = false, updatable = false)
	private UUID communityId;

	@Column(name = "title", columnDefinition = "VARCHAR(255) NOT NULL")
	private String title;

	@Column(name = "description", columnDefinition = "TEXT NOT NULL")
	private String description;

	@Column(name = "status", columnDefinition = "VARCHAR(255) NOT NULL")
	@Enumerated(EnumType.STRING)
	private IssueStatus status = IssueStatus.OPEN;

	@Column(name = "urgency_level", columnDefinition = "VARCHAR(50)")
	@Enumerated(EnumType.STRING)
	private UrgencyLevel urgencyLevel;

	@ManyToOne
	@JoinColumn(name = "reporter_id", columnDefinition = "UUID NOT NULL")
	private User reporter;

	@ManyToOne
	@JoinColumn(name = "assigned_to_user_id", columnDefinition = "UUID")
	private User assignedTo;

	@ManyToOne
	@JoinColumn(name = "category_id", columnDefinition = "UUID NOT NULL")
	private IssueCategory category;

	@Column(name = "custom_category", columnDefinition = "VARCHAR(255)")
	private String customCategory;

	@Column(name = "location", columnDefinition = "VARCHAR(255)")
	private String location;

	@Column(name = "latitude", columnDefinition = "DOUBLE PRECISION")
	private Double latitude;

	@Column(name = "longitude", columnDefinition = "DOUBLE PRECISION")
	private Double longitude;

	@Column(name = "resolved_at", columnDefinition = "TIMESTAMP")
	private LocalDateTime resolvedAt;

	@Column(name = "resolution_notes", columnDefinition = "VARCHAR(2000)")
	private String resolutionNotes;

	@Transient
	@Builder.Default
	private List<AttachmentReference> attachmentReferences = new ArrayList<>();
}
