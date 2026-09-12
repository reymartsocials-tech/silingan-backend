package com.ria.olita.tech.silingan.rest;

import com.ria.olita.tech.silingan.dto.res.ApiResponse;
import com.ria.olita.tech.silingan.dto.res.CommunityResponse;
import com.ria.olita.tech.silingan.security.scope.SkipCommunityScopeCheck;
import com.ria.olita.tech.silingan.service.CommunityService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/communities")
@RequiredArgsConstructor
@Tag(name = "Public Community", description = "Public community lookup APIs")
@SkipCommunityScopeCheck(reason = "Public community lookup does not require community scope")
public class PublicCommunityController {

	private final CommunityService communityService;

	@PermitAll
	@GetMapping("/code/{code}")
	@Operation(summary = "Get community by code (public lookup)")
	public ResponseEntity<ApiResponse<CommunityResponse>> getByCode(
		@Parameter(description = "Community code", example = "GR-001") @PathVariable String code) {
		CommunityResponse response = communityService.getByCode(code);
		return ResponseEntity.ok(ApiResponse.success(response));
	}

	@PermitAll
	@GetMapping("/search")
	@Operation(
		summary = "Search communities by name or code",
		description = "Search for communities using community name or community code. The search is case-insensitive and supports partial matching."
	)
	public ResponseEntity<ApiResponse<List<CommunityResponse>>> searchCommunities(
		@Parameter(
			description = "Search term to match against community name or code",
			example = "Greenbelt",
			required = true
		) @RequestParam String searchTerm) {
		List<CommunityResponse> responses = communityService.searchByCommunityNameOrCode(searchTerm);
		return ResponseEntity.ok(ApiResponse.success(responses));
	}
}
