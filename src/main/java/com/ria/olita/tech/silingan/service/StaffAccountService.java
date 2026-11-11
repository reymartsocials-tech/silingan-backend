package com.ria.olita.tech.silingan.service;

import java.util.UUID;

import com.ria.olita.tech.silingan.dto.req.CreateStaffAccountRequest;
import com.ria.olita.tech.silingan.dto.req.EditStaffInfoRequest;
import com.ria.olita.tech.silingan.dto.res.StaffAccountResponse;

/**
 * Service for staff account management within a community.
 * Handles creating, editing, and toggling activation of staff accounts.
 * Enforces business rules such as protecting the final community admin.
 */
public interface StaffAccountService {

	/**
	 * Create a new staff account directly without invitation.
	 * 
	 * @param communityId The community ID
	 * @param request Staff account creation request with all required fields
	 * @return Response with created staff account details
	 */
	StaffAccountResponse createStaffAccount(UUID communityId, CreateStaffAccountRequest request);

	/**
	 * Edit staff member's personal information (name, mobile).
	 * Email and role changes use separate endpoints.
	 * 
	 * @param communityId The community ID
	 * @param staffUserId The staff user ID to update
	 * @param request Edit request with new staff information
	 * @return Updated staff account response
	 */
	StaffAccountResponse editStaffInfo(UUID communityId, UUID staffUserId, EditStaffInfoRequest request);

	/**
	 * Deactivate a staff account (set status to INACTIVE).
	 * Inactive staff cannot access the system.
	 * Prevents deactivating the final Community Admin.
	 * 
	 * @param communityId The community ID
	 * @param staffUserId The staff user ID to deactivate
	 * @return Updated staff account response with INACTIVE status
	 * @throws IllegalStateException if attempting to deactivate last community admin
	 */
	StaffAccountResponse deactivateStaff(UUID communityId, UUID staffUserId);

	/**
	 * Activate a staff account (set status to ACTIVE).
	 * Allows previously deactivated staff to access the system again.
	 * 
	 * @param communityId The community ID
	 * @param staffUserId The staff user ID to activate
	 * @return Updated staff account response with ACTIVE status
	 */
	StaffAccountResponse activateStaff(UUID communityId, UUID staffUserId);

	/**
	 * Check if a user is the last active Community Admin in the community.
	 * 
	 * @param communityId The community ID
	 * @param userId The user ID to check
	 * @return true if user is the last community admin, false otherwise
	 */
	boolean isLastCommunityAdmin(UUID communityId, UUID userId);
}
