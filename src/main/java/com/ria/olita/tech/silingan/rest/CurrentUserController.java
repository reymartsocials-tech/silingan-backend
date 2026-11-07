package com.ria.olita.tech.silingan.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.res.CurrentUserResponse;
import com.ria.olita.tech.silingan.service.CurrentUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Current User", description = "Profile, roles and permissions of the authenticated caller")
public class CurrentUserController {

	private final CurrentUserService currentUserService;

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	@Operation(summary = "Get the authenticated user with realm roles and community permissions")
	public ResponseEntity<CurrentUserResponse> getCurrentUser() {
		return ResponseEntity.ok(currentUserService.getCurrentUser());
	}
}
