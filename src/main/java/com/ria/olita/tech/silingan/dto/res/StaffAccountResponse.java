package com.ria.olita.tech.silingan.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.UserStatus;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

/**
 * Response after editing staff information or creating staff account.
 */
public record StaffAccountResponse(
	UUID userId,
	String firstName,
	String lastName,
	String email,
	String mobileNumber,
	StaffRoleCode staffRole,
	UserStatus status,
	LocalDateTime updatedAt
) {
}
