package com.ria.olita.tech.silingan.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ria.olita.tech.silingan.dto.req.CreateUserRequest;
import com.ria.olita.tech.silingan.service.UserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;

@RestController
@Slf4j
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@PreAuthorize("hasRole('PLATFORM_ADMIN') or hasRole('COMMUNITY_ADMIN')")
	@PostMapping
	public ResponseEntity<Void> createUser(@Valid @RequestBody CreateUserRequest request) {
		log.info("Create user request received for username: {}", request.username());
		userService.createUser(request);
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}
}
