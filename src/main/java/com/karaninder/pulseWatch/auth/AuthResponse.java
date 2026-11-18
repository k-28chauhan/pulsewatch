package com.karaninder.pulseWatch.auth;

import java.time.Instant;

/** Send the token back as "Authorization: Bearer &lt;token&gt;" on every later request. */
public record AuthResponse(String token, String tokenType, Instant expiresAt) {

	public static AuthResponse bearer(String token, Instant expiresAt) {
		return new AuthResponse(token, "Bearer", expiresAt);
	}
}
