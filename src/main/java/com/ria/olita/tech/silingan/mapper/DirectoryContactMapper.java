package com.ria.olita.tech.silingan.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.ria.olita.tech.silingan.dto.req.CreateDirectoryContactRequest;
import com.ria.olita.tech.silingan.dto.res.DirectoryContactResponse;
import com.ria.olita.tech.silingan.dto.res.ResidentDirectoryContactResponse;
import com.ria.olita.tech.silingan.entity.DirectoryContact;
import com.ria.olita.tech.silingan.entity.User;

@Mapper(componentModel = "spring",
	unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DirectoryContactMapper {

	@Mapping(target = "createdBy", source = "createdBy.id")
	@Mapping(target = "updatedBy", source = "updatedBy.id")
	DirectoryContactResponse toResponse(DirectoryContact contact);

	ResidentDirectoryContactResponse toResidentResponse(DirectoryContact contact);

	/**
	 * Identity, ownership and the active flag are never taken from the request payload; the service
	 * assigns them from the security context and the project defaults.
	 */
	@Mapping(target = "id", ignore = true)
	@Mapping(target = "community", ignore = true)
	@Mapping(target = "communityId", ignore = true)
	@Mapping(target = "displayOrder", ignore = true)
	@Mapping(target = "active", ignore = true)
	DirectoryContact toEntity(CreateDirectoryContactRequest request);

	default UUID map(User user) {
		return user != null ? user.getId() : null;
	}
}
