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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(
	name = "attachment_references",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_attachment_owner",
			columnNames = {"attachment_id", "owner_type", "owner_id", "display_order"}
		)
	}
)
@Builder
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
@SQLRestriction("deleted = false")
public class AttachmentReference extends BaseEntity {

	@Id
	@GeneratedValue
	@Column(columnDefinition = "UUID")
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "attachment_id", columnDefinition = "UUID NOT NULL")
	private Attachment attachment;

	@Column(name = "attachment_id", insertable = false, updatable = false)
	private UUID attachmentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "owner_type", nullable = false, length = 32)
	private AttachmentOwnerType ownerType;

	@Column(name = "owner_id", nullable = false, columnDefinition = "UUID")
	private UUID ownerId;

	@Column(name = "display_order", nullable = false)
	@Builder.Default
	private Integer displayOrder = 0;
}
