package com.karaninder.pulseWatch.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AuthService service;

	@MockitoBean
	JwtDecoder jwtDecoder;

	private final AuthResponse token = AuthResponse.bearer("the-token", Instant.parse("2026-11-10T12:00:00Z"));

	@Test
	void registerIsPublicAndReturns201WithAToken() throws Exception {
		when(service.register(any(RegisterRequest.class), any(String.class))).thenReturn(token);

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "ann@example.com", "password": "correct horse"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").value("the-token"))
				.andExpect(jsonPath("$.tokenType").value("Bearer"));
	}

	@Test
	void registerRejectsABadEmailAndAShortPasswordListingBothFields() throws Exception {
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "not-an-email", "password": "short"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.email").exists())
				.andExpect(jsonPath("$.errors.password").exists());

		verifyNoInteractions(service);
	}

	@Test
	void aPasswordLongerThanBcryptCanUseIsRejected() throws Exception {
		String tooLong = "x".repeat(73);

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"ann@example.com\", \"password\": \"" + tooLong + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.password").exists());
	}

	@Test
	void anAlreadyRegisteredEmailIs409() throws Exception {
		when(service.register(any(RegisterRequest.class), any(String.class))).thenThrow(new EmailAlreadyRegisteredException());

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "ann@example.com", "password": "correct horse"}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("An account with this email already exists"));
	}

	@Test
	void loginIsPublicAndReturnsAToken() throws Exception {
		when(service.login(any(LoginRequest.class), any(String.class))).thenReturn(token);

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "ann@example.com", "password": "correct horse"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("the-token"));
	}

	@Test
	void badCredentialsAre401WithAGenericMessage() throws Exception {
		when(service.login(any(LoginRequest.class), any(String.class))).thenThrow(new InvalidCredentialsException());

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "ann@example.com", "password": "nope"}
								"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Invalid email or password"));
	}

	@Test
	void theClientAddressIsPassedOnForRateLimiting() throws Exception {
		when(service.login(any(LoginRequest.class), any(String.class))).thenReturn(token);

		mockMvc.perform(post("/api/auth/login").with(request -> {
					request.setRemoteAddr("203.0.113.9");
					return request;
				}).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "ann@example.com", "password": "correct horse"}
								"""))
				.andExpect(status().isOk());

		verify(service).login(any(LoginRequest.class), org.mockito.ArgumentMatchers.eq("203.0.113.9"));
	}

	@Test
	void tooManyAttemptsAre429WithARetryAfterHeader() throws Exception {
		when(service.login(any(LoginRequest.class), any(String.class))).thenThrow(new TooManyAttemptsException(90));

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "ann@example.com", "password": "guess"}
								"""))
				.andExpect(status().isTooManyRequests())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "90"))
				.andExpect(jsonPath("$.detail").value("Too many attempts. Try again in 90 seconds."));
	}
}
