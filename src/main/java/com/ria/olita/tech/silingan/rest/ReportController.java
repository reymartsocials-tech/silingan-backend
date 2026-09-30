package com.ria.olita.tech.silingan.rest;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.req.CreateReportRequest;
import com.ria.olita.tech.silingan.dto.req.UpdateReportRequest;
import com.ria.olita.tech.silingan.dto.res.ReportResponse;
import com.ria.olita.tech.silingan.entity.IssueStatus;
import com.ria.olita.tech.silingan.service.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated() && hasRole('RESIDENT')")
@Tag(name = "Reports", description = "Community issue/report APIs")
public class ReportController {

	private final ReportService reportService;

	@PostMapping
	@Operation(summary = "Submit report")
	public ResponseEntity<ReportResponse> submitReport(@Valid @RequestBody CreateReportRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(reportService.submitReport(request));
	}

	@GetMapping
	@Operation(summary = "Get paginated reports")
	public ResponseEntity<Page<ReportResponse>> getPaginatedReports(
		@Parameter(description = "Community ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@RequestParam UUID communityId,
		@Parameter(description = "Optional report status filter", example = "OPEN")
		@RequestParam(required = false) IssueStatus status,
		Pageable pageable
	) {
		return ResponseEntity.ok(reportService.getReports(communityId, status, pageable));
	}

	@GetMapping("/{reportId}")
	@Operation(summary = "Get report details")
	public ResponseEntity<ReportResponse> getReportDetails(
		@Parameter(description = "Report ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID reportId
	) {
		return ResponseEntity.ok(reportService.getReportDetails(reportId));
	}

	@PutMapping("/{reportId}")
	@Operation(summary = "Update report")
	public ResponseEntity<ReportResponse> updateReport(
		@Parameter(description = "Report ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID reportId,
		@RequestBody UpdateReportRequest request
	) {
		return ResponseEntity.ok(reportService.updateReport(reportId, request));
	}

	@PutMapping("/{reportId}/cancel")
	@Operation(summary = "Cancel report")
	public ResponseEntity<ReportResponse> cancelReport(
		@Parameter(description = "Report ID", example = "550e8400-e29b-41d4-a716-446655440000")
		@PathVariable UUID reportId
	) {
		return ResponseEntity.ok(reportService.cancelReport(reportId));
	}
}
