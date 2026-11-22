package com.ria.olita.tech.silingan.dto.res;

import java.util.List;
import java.util.UUID;

public record StaffRoutingDependenciesResponse(
	UUID staffUserId,
	UUID staffMembershipId,
	List<RoutedCategoryResponse> routedCategories,
	long unresolvedAssignedReportCount,
	boolean canDeactivate
) {
	public record RoutedCategoryResponse(UUID categoryId, String categoryName) {
	}
}
