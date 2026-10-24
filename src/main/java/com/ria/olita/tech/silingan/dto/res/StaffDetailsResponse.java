package com.ria.olita.tech.silingan.dto.res;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.InvitationStatus;
import com.ria.olita.tech.silingan.entity.UserStatus;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

public record StaffDetailsResponse(
	// Basic Information
	UUID userId,
	
	// Personal Information
	String firstName,
	String lastName,
	
	// Contact Information
	String email,
	String mobileNumber,
	
	// Employment Information
	StaffRoleCode position,
	LocalDateTime joinedAt,
	
	// Account and Invitation Status
	UserStatus accountStatus,
	InvitationStatus invitationStatus,
	
	// Important Account Dates
	LocalDateTime accountCreatedAt,
	LocalDateTime accountUpdatedAt,
	LocalDateTime invitedAt,
	LocalDateTime acceptedAt,
	LocalDateTime expiresAt,
	LocalDateTime revokedAt,
	
	// Audit Information
	String createdByName,
	String updatedByName
) {
}
