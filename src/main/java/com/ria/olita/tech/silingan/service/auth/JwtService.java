package com.ria.olita.tech.silingan.service.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

import com.ria.olita.tech.silingan.entity.User;

public interface JwtService {

	String generateToken(User user, List<String> roles, UUID communityId);

	Optional<Jwt> parse(String token);

	boolean validate(String token);
}

