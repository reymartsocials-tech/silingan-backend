package com.ria.olita.tech.silingan.entity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
  name = "announcements",
  indexes = {
    @Index(name = "idx_title_community", columnList = "title, community_id")
  }
)
@Builder
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
@Getter
@Setter
public class Announcement extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "UUID")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_id", columnDefinition = "UUID NOT NULL")
    private Community community;

    @Column(name = "community_id", insertable = false, updatable = false)
    private UUID communityId;

    // Short headline shown in lists
    @Column(name = "title", nullable = false)
    private String title;

    // Main announcement body
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // Advisory | Event | Emergency
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private AnnouncementCategory category;

    // Draft / Published / Archived
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AnnouncementStatus status;

    // Highlight important announcements
    @Column(name = "is_pinned", nullable = false)
    @Builder.Default
    private boolean pinned = false;

    // Optional scheduled publishing
    @Column(name = "publish_at")
    private Instant publishAt;

    // Set once when the announcement transitions from DRAFT to PUBLISHED
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_by", columnDefinition = "UUID")
    private User publishedBy;

    @Transient
    @Builder.Default
    private List<AttachmentReference> attachmentReferences = new ArrayList<>();

}
