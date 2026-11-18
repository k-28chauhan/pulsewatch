package com.karaninder.pulseWatch.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * @param jwtSecret key that signs and verifies tokens (HS256 needs at least 32 bytes).
 *                  The value in application.yaml is for local development ONLY: set PULSEWATCH_SECURITY_JWT_SECRET in production.
 * @param tokenTtl  how long a token stays valid
 */
@ConfigurationProperties(prefix = "pulsewatch.security")
public record SecurityProperties(String jwtSecret, @DefaultValue("PT12H") Duration tokenTtl) {

	public SecurityProperties {
		if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalArgumentException("pulsewatch.security.jwt-secret must be at least 32 bytes long");
		}
	}
}
