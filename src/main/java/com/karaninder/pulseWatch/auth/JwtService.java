package com.karaninder.pulseWatch.auth;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/** Creates signed access tokens. The token's subject is the user's id (the only thing the rest of the app reads). */
@Service
public class JwtService {

	private final JwtEncoder encoder;
	private final SecurityProperties properties;
	private final Clock clock;

	public JwtService(JwtEncoder encoder, SecurityProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	public AuthResponse issueFor(User user) {
		Instant issuedAt = clock.instant();
		Instant expiresAt = issuedAt.plus(properties.tokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("pulsewatch")
				.subject(String.valueOf(user.getId()))
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.claim("email", user.getEmail())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return AuthResponse.bearer(token, expiresAt);
	}
}
