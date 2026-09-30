package com.ria.olita.tech.silingan.dto.req;

import java.util.UUID;

import com.ria.olita.tech.silingan.entity.IssueStatus;

public record UpdateReportRequest(
	String title,
	String description,
	UUID categoryId,
	String customCategory,
	String imageUrl,
	String location,
	Double latitude,
	Double longitude,
	IssueStatus status
) {
}
