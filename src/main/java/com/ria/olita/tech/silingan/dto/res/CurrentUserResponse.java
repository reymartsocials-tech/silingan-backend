package com.ria.olita.tech.silingan.dto.res;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.ria.olita.tech.silingan.entity.SilinganRealmRole;
import com.ria.olita.tech.silingan.entity.UserStatus;
import com.ria.olita.tech.silingan.entity.rbac.PermissionEnum;
import com.ria.olita.tech.silingan.entity.rbac.StaffRoleCode;

/**
 * Aggregated view of the authenticated caller: profile, realm roles, and the effective staff role
 * plus permissions for every community the caller belongs to.
 *
 * <p>{@code roleCode} and {@code permissions} are the convenience projection of the currently
 * selected community so clients that only operate in one community do not need to search
 * {@code communities}.
 */
public record CurrentUserResponse(
	UUID userId,
	String keycloakUserId,
	String username,
	String email,
	String mobileNumber,
	String firstName,
	String lastName,
	UserStatus status,
	List<SilinganRealmRole> roles,
	UUID selectedCommunityId,
	StaffRoleCode roleCode,
	Set<PermissionEnum> permissions,
	Map<UUID, String> communities
) {
}
