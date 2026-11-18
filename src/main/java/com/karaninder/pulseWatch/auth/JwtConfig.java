package com.karaninder.pulseWatch.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * One shared secret key, used two ways: the encoder SIGNS tokens at login, the decoder VERIFIES them on
 * every request. The decoder also rejects expired tokens. (HS256 = symmetric: whoever can verify can also sign.)
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class JwtConfig {

	@Bean
	JwtEncoder jwtEncoder(SecurityProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(key(properties)));
	}

	@Bean
	JwtDecoder jwtDecoder(SecurityProperties properties) {
		return NimbusJwtDecoder.withSecretKey(key(properties)).macAlgorithm(MacAlgorithm.HS256).build();
	}

	private static SecretKey key(SecurityProperties properties) {
		return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}
}
