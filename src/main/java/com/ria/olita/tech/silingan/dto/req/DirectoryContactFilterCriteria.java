package com.ria.olita.tech.silingan.dto.req;

import com.ria.olita.tech.silingan.entity.DirectoryContactCategory;
import com.ria.olita.tech.silingan.entity.DirectoryContactType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Filter criteria for directory contacts")
public class DirectoryContactFilterCriteria {

	@Schema(description = "Filter by contact category", example = "EMERGENCY_SERVICE")
	private DirectoryContactCategory category;

	@Schema(description = "Filter by contact type", example = "POLICE")
	private DirectoryContactType contactType;

	@Schema(description = "Filter by active status. Omit to return both active and inactive contacts.", example = "true")
	private Boolean active;

	@Schema(description = "Search keyword matched against the display name")
	private String keyword;
}
