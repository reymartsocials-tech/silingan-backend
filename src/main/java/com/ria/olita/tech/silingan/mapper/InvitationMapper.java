package com.ria.olita.tech.silingan.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.ria.olita.tech.silingan.dto.res.InvitationSummaryResponse;
import com.ria.olita.tech.silingan.entity.Invitation;

@Mapper(componentModel = "spring")
public interface InvitationMapper {

	@Mapping(target = "invitationId", source = "id")
	@Mapping(target = "status", source = "status", qualifiedByName = "statusToString")
	@Mapping(target = "invitedName", source = "email")
	@Mapping(target = "invitedByName", expression = "java(buildInvitedByName(invitation))")
	InvitationSummaryResponse toSummaryResponse(Invitation invitation);

	@Named("statusToString")
	default String statusToString(Object status) {
		return status != null ? status.toString() : null;
	}

	default String buildInvitedByName(Invitation invitation) {
		if (invitation.getInvitedBy() == null) {
			return null;
		}
		String firstName = invitation.getInvitedBy().getFirstName();
		String lastName = invitation.getInvitedBy().getLastName();
		
		if (firstName == null && lastName == null) {
			return null;
		}
		return (firstName != null ? firstName : "") +
			(lastName != null ? " " + lastName : "");
	}
}
