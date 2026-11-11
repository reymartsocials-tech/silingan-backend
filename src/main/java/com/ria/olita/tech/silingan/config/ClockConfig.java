package com.ria.olita.tech.silingan.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Entity timestamps are stored as zone-less {@code LocalDateTime} values written in the JVM default
 * zone, so the shared clock uses that same zone to keep period boundaries consistent with them.
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}
