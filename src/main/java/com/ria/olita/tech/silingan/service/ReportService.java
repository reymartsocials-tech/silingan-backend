package com.ria.olita.tech.silingan.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ria.olita.tech.silingan.dto.req.CreateReportRequest;
import com.ria.olita.tech.silingan.dto.req.UpdateReportRequest;
import com.ria.olita.tech.silingan.dto.res.ReportResponse;
import com.ria.olita.tech.silingan.entity.IssueStatus;

public interface ReportService {

	ReportResponse submitReport(CreateReportRequest request);

	Page<ReportResponse> getReports(UUID communityId, IssueStatus status, Pageable pageable);

	ReportResponse getReportDetails(UUID reportId);

	ReportResponse updateReport(UUID reportId, UpdateReportRequest request);

	ReportResponse cancelReport(UUID reportId);
}
