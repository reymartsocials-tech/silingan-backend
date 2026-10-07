package com.ria.olita.tech.silingan.config;

import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.ria.olita.tech.silingan.domain.CommunityId;

/**
 * Spring converter that automatically converts path variable strings to CommunityId.
 * This enables Spring to inject CommunityId directly in controller method parameters.
 */
@Component
public class CommunityIdConverter implements Converter<String, CommunityId> {

	@Override
	public CommunityId convert(String source) {
		return CommunityId.of(UUID.fromString(source));
	}
}
