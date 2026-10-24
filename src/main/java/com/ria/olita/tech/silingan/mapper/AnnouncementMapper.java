package com.ria.olita.tech.silingan.mapper;

import com.ria.olita.tech.silingan.dto.req.CreateAnnouncementRequest;
import com.ria.olita.tech.silingan.dto.res.AnnouncementResponse;
import com.ria.olita.tech.silingan.entity.Announcement;
import com.ria.olita.tech.silingan.entity.User;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring",
	unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AnnouncementMapper {

	@Mapping(target = "createdBy", source = "createdBy.id")
	@Mapping(target = "updatedBy", source = "updatedBy.id")
	@Mapping(target = "publishedBy", source = "publishedBy.id")
	AnnouncementResponse toResponse(Announcement announcement);

	/**
	 * Server-managed state (identity, ownership, status and publication fields) is never
	 * taken from the request payload; the service assigns it from the security context.
	 */
	@Mapping(target = "id", ignore = true)
	@Mapping(target = "community", ignore = true)
	@Mapping(target = "communityId", ignore = true)
	@Mapping(target = "publishAt", ignore = true)
	@Mapping(target = "publishedAt", ignore = true)
	@Mapping(target = "publishedBy", ignore = true)
	@Mapping(target = "attachmentReferences", ignore = true)
	@Mapping(target = "status", expression = "java(com.ria.olita.tech.silingan.entity.AnnouncementStatus.DRAFT)")
	Announcement toEntity(CreateAnnouncementRequest request);

	default UUID map(User user) {
		return user != null ? user.getId() : null;
	}
}
