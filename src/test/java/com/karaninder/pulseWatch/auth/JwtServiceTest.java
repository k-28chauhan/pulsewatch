package com.karaninder.pulseWatch.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Uses the REAL encoder and decoder from JwtConfig: a token we sign must verify, and nothing else may. */
class JwtServiceTest {

	private static final String SECRET = "unit-test-secret-that-is-long-enough-0123456789";
	private static final Instant NOW = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

	private final JwtConfig config = new JwtConfig();
	private final SecurityProperties properties = new SecurityProperties(SECRET, Duration.ofHours(1));
	private final JwtEncoder encoder = config.jwtEncoder(properties);
	private final JwtDecoder decoder = config.jwtDecoder(properties);

	private User ann() {
		User user = new User("ann@example.com", "hash");
		ReflectionTestUtils.setField(user, "id", 42L);
		return user;
	}

	private JwtService serviceAt(Instant now, SecurityProperties props, JwtEncoder jwtEncoder) {
		return new JwtService(jwtEncoder, props, Clock.fixed(now, ZoneOffset.UTC));
	}

	@Test
	void aTokenCarriesTheUserIdAndExpiresAfterTheConfiguredTime() {
		AuthResponse response = serviceAt(NOW, properties, encoder).issueFor(ann());

		Jwt jwt = decoder.decode(response.token());

		assertThat(jwt.getSubject()).isEqualTo("42");
		assertThat(AuthenticatedUser.id(jwt)).isEqualTo(42L);
		assertThat(jwt.<String>getClaim("email")).isEqualTo("ann@example.com");
		assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
		assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
		assertThat(response.tokenType()).isEqualTo("Bearer");
	}

	@Test
	void anExpiredTokenIsRejected() {
		Instant longAgo = NOW.minus(Duration.ofHours(3)); // issued 3 h ago with a 1 h lifetime
		String token = serviceAt(longAgo, properties, encoder).issueFor(ann()).token();

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
	}

	@Test
	void aTokenSignedWithAnotherSecretIsRejected() {
		SecurityProperties other = new SecurityProperties("a-completely-different-secret-0123456789abc", Duration.ofHours(1));
		String forged = serviceAt(NOW, other, config.jwtEncoder(other)).issueFor(ann()).token();

		assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(BadJwtException.class);
	}

	@Test
	void aTamperedPayloadIsRejected() {
		String token = serviceAt(NOW, properties, encoder).issueFor(ann()).token();
		String[] parts = token.split("\\.");
		// swap in the payload of a token for user 1, keeping the original signature
		User admin = new User("x@example.com", "hash");
		ReflectionTestUtils.setField(admin, "id", 1L);
		String otherPayload = serviceAt(NOW, properties, encoder).issueFor(admin).token().split("\\.")[1];
		String tampered = parts[0] + "." + otherPayload + "." + parts[2];

		assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(BadJwtException.class);
	}

	@Test
	void aShortSecretIsRefusedAtStartup() {
		assertThatThrownBy(() -> new SecurityProperties("too-short", Duration.ofHours(1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("at least 32 bytes");
		assertThatThrownBy(() -> new SecurityProperties(null, Duration.ofHours(1)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
