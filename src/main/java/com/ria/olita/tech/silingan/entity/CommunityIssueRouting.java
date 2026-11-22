package com.ria.olita.tech.silingan.entity;

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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The single primary routing destination of one issue category inside one community.
 *
 * <p>A STAFF destination references the staff member's community membership rather than a global
 * user, so a route is automatically invalid once that membership stops being eligible.
 */
@Entity
@Table(name = "community_issue_routings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
@SQLRestriction("deleted = false")
public class CommunityIssueRouting extends BaseEntity {

	@Id
	@GeneratedValue
	@EqualsAndHashCode.Include
	@Column(columnDefinition = "UUID")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "community_id", nullable = false)
	private Community community;

	@Column(name = "community_id", insertable = false, updatable = false)
	private UUID communityId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "issue_category_id", nullable = false)
	private IssueCategory category;

	@Column(name = "issue_category_id", insertable = false, updatable = false)
	private UUID categoryId;

	@Enumerated(EnumType.STRING)
	@Column(name = "destination_type", nullable = false, length = 30)
	private IssueRoutingDestination destinationType;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigned_staff_membership_id")
	private UserCommunity assignedStaffMembership;

	@Column(name = "assigned_staff_membership_id", insertable = false, updatable = false)
	private UUID assignedStaffMembershipId;

	@Builder.Default
	@Column(name = "active", nullable = false)
	private Boolean active = true;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;
}
