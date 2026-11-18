package com.karaninder.pulseWatch.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/** Reads "who is calling?" out of the verified token. Controllers pass this id to services; they never decide access themselves. */
public final class AuthenticatedUser {

	private AuthenticatedUser() {
	}

	public static Long id(Jwt jwt) {
		return Long.valueOf(jwt.getSubject());
	}
}
