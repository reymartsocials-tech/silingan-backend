package com.ria.olita.tech.silingan.entity;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.ria.olita.tech.silingan.entity.base.BaseEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
	name = "communities",
	indexes = {
		@Index(name = "idx_community_code", columnList = "community_code"),
		@Index(name = "idx_tenant_community", columnList = "tenant_id, community_code")
	},
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_tenant_community",
			columnNames = {"tenant_id", "community_code"}
		)
	}
)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@SQLRestriction("deleted = false")
@Setter
@Getter
public class Community extends BaseEntity {

	@Id
	@GeneratedValue
	@Column(columnDefinition = "UUID")
	private UUID id;

	@Column(name = "community_code", columnDefinition = "VARCHAR(255) NOT NULL UNIQUE")
	private String communityCode;

	@Column(name = "system_gen_code")
	private String systemGenCode;

	@Column(name = "name", columnDefinition = "VARCHAR(255) NOT NULL")
	private String name;

	@OneToOne
	@JoinColumn(name = "logo_id")
	private Media logo;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "logo_attachment_id")
	private Attachment logoAttachment;

	@Column(name = "logo_attachment_id", insertable = false, updatable = false)
	private UUID logoAttachmentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", columnDefinition = "VARCHAR(255)")
	private CommunityType type;

	@OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "address_id", columnDefinition = "UUID")
	private Address address;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", columnDefinition = "VARCHAR(255)")
	private CommunityStatus status = CommunityStatus.ACTIVE;


	@ManyToOne(fetch = FetchType.LAZY) // future proof
	@JoinColumn(name = "tenant_id")
	private Tenant tenant;

	@Column(name = "tenant_id", insertable = false, updatable = false)
	private UUID tenantId;


	@OneToMany(mappedBy = "community", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<UserCommunity> members = new ArrayList<>();

}
