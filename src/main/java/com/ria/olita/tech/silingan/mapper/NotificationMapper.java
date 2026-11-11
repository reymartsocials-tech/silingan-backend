package com.ria.olita.tech.silingan.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.ria.olita.tech.silingan.dto.res.NotificationResponse;
import com.ria.olita.tech.silingan.entity.Notification;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NotificationMapper {

	@Mapping(target = "type", source = "notificationType")
	NotificationResponse toResponse(Notification notification);
}
