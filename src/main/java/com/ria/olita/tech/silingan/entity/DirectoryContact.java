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
import jakarta.persistence.Index;
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
 * A phone contact published in a community's directory, such as the PMO, security, or an emergency
 * hotline. Each contact belongs to exactly one community; the directory of one community is never
 * reachable from another.
 *
 * <p>Deactivating hides a contact from residents while keeping it and its audit trail available to
 * staff, which is why {@code active} is separate from the inherited soft-delete flag.
 */
@Entity
@Table(
	name = "directory_contacts",
	indexes = {
		@Index(name = "idx_directory_contact_community", columnList = "community_id"),
		@Index(
			name = "idx_directory_contact_resident_feed",
			columnList = "community_id, is_active, category, display_order"
		)
	}
)
@Builder
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@SQLRestriction("deleted = false")
@Getter
@Setter
public class DirectoryContact extends BaseEntity {

	/** Used when the client does not supply a display order, so sorting stays deterministic. */
	public static final int DEFAULT_DISPLAY_ORDER = 0;

	@Id
	@GeneratedValue
	@Column(columnDefinition = "UUID")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "community_id", columnDefinition = "UUID NOT NULL")
	private Community community;

	/** Read-only mirror of the foreign key so queries can filter without joining. */
	@Column(name = "community_id", insertable = false, updatable = false)
	private UUID communityId;

	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 32)
	private DirectoryContactCategory category;

	@Enumerated(EnumType.STRING)
	@Column(name = "contact_type", nullable = false, length = 48)
	private DirectoryContactType contactType;

	@Column(name = "display_name", nullable = false, length = 150)
	private String displayName;

	@Column(name = "primary_phone_number", nullable = false, length = 32)
	private String primaryPhoneNumber;

	@Column(name = "secondary_phone_number", length = 32)
	private String secondaryPhoneNumber;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "operating_hours", length = 150)
	private String operatingHours;

	@Column(name = "address", length = 255)
	private String address;

	@Column(name = "email", length = 255)
	private String email;

	/** Lower values surface first within a category. */
	@Column(name = "display_order", nullable = false)
	@Builder.Default
	private int displayOrder = DEFAULT_DISPLAY_ORDER;

	/** Only active contacts are returned to residents. */
	@Column(name = "is_active", nullable = false)
	@Builder.Default
	private boolean active = true;
}
