package com.karaninder.pulseWatch.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call what. Everything needs a valid JWT, except the few public paths below.
 * <ul>
 *   <li>stateless: no session, no cookie; the token is the only credential</li>
 *   <li>CSRF protection is off: CSRF attacks abuse cookies the browser sends automatically, and we use none</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests
						.requestMatchers("/api/auth/**").permitAll()
						// Public for local development. Before a public deployment, restrict /actuator/prometheus at the network level.
						.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
						.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
		return http.build();
	}

	/** BCrypt: slow on purpose, salted automatically, so a stolen table cannot be cracked quickly. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
