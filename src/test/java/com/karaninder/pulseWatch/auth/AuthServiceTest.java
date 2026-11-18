package com.karaninder.pulseWatch.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	UserRepository users;

	@Mock
	JwtService jwtService;

	@Mock
	AttemptLimiter limiter;

	private static final String IP = "203.0.113.7";

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // low cost: tests should be fast
	private final AuthResponse token = AuthResponse.bearer("the-token", Instant.parse("2026-11-10T12:00:00Z"));

	private AuthService service;

	@BeforeEach
	void createService() {
		service = new AuthService(users, encoder, jwtService, limiter);
	}

	@Test
	void registerStoresOnlyAHashAndANormalisedEmailThenReturnsAToken() {
		when(users.existsByEmail("ann@example.com")).thenReturn(false);
		when(users.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
		when(jwtService.issueFor(any(User.class))).thenReturn(token);

		AuthResponse response = service.register(new RegisterRequest("  Ann@Example.COM ", "correct horse"), IP);

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(users).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("ann@example.com");
		assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("correct horse").startsWith("$2"); // BCrypt
		assertThat(encoder.matches("correct horse", saved.getValue().getPasswordHash())).isTrue();
		assertThat(response).isEqualTo(token);
	}

	@Test
	void registeringAnExistingEmailIsRefused() {
		when(users.existsByEmail("ann@example.com")).thenReturn(true);

		assertThatThrownBy(() -> service.register(new RegisterRequest("Ann@example.com", "correct horse"), IP))
				.isInstanceOf(EmailAlreadyRegisteredException.class);
		verify(users, never()).saveAndFlush(any());
	}

	@Test
	void aRaceOnTheUniqueEmailIndexBecomesTheSameError() {
		when(users.existsByEmail("ann@example.com")).thenReturn(false);
		when(users.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("uq_users_email"));

		assertThatThrownBy(() -> service.register(new RegisterRequest("ann@example.com", "correct horse"), IP))
				.isInstanceOf(EmailAlreadyRegisteredException.class);
	}

	@Test
	void loginWithTheRightPasswordReturnsAToken() {
		User ann = new User("ann@example.com", encoder.encode("correct horse"));
		when(users.findByEmail("ann@example.com")).thenReturn(Optional.of(ann));
		when(jwtService.issueFor(ann)).thenReturn(token);

		assertThat(service.login(new LoginRequest("ANN@example.com", "correct horse"), IP)).isEqualTo(token);
	}

	@Test
	void aWrongPasswordIsRejected() {
		User ann = new User("ann@example.com", encoder.encode("correct horse"));
		when(users.findByEmail("ann@example.com")).thenReturn(Optional.of(ann));

		assertThatThrownBy(() -> service.login(new LoginRequest("ann@example.com", "wrong"), IP))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessage("Invalid email or password");
		verify(jwtService, never()).issueFor(any());
	}

	@Test
	void anUnknownEmailGivesTheSameErrorAsAWrongPassword() {
		when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "whatever"), IP))
				.isInstanceOf(InvalidCredentialsException.class)
				.hasMessage("Invalid email or password");
	}

	@Test
	void theLegacyPlaceholderAccountCanNeverLogIn() {
		// V7 gives old monitors an owner whose hash is "!", which is not a BCrypt hash
		User legacy = new User("legacy@pulsewatch.local", "!");
		when(users.findByEmail("legacy@pulsewatch.local")).thenReturn(Optional.of(legacy));

		assertThatThrownBy(() -> service.login(new LoginRequest("legacy@pulsewatch.local", "!"), IP))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	// ---- rate limiting ----

	@Test
	void loginCountsAnAttemptPerIpAndPerEmailAndClearsTheEmailCounterOnSuccess() {
		User ann = new User("ann@example.com", encoder.encode("correct horse"));
		when(users.findByEmail("ann@example.com")).thenReturn(Optional.of(ann));
		when(jwtService.issueFor(ann)).thenReturn(token);

		service.login(new LoginRequest("Ann@example.com", "correct horse"), IP);

		verify(limiter).record("login-ip:" + IP, AuthService.LOGINS_PER_IP, AuthService.WINDOW);
		verify(limiter).record("login-email:ann@example.com", AuthService.LOGINS_PER_EMAIL, AuthService.WINDOW);
		verify(limiter).reset("login-email:ann@example.com");
	}

	@Test
	void aWrongPasswordDoesNotClearTheCounter() {
		User ann = new User("ann@example.com", encoder.encode("correct horse"));
		when(users.findByEmail("ann@example.com")).thenReturn(Optional.of(ann));

		assertThatThrownBy(() -> service.login(new LoginRequest("ann@example.com", "wrong"), IP)).isInstanceOf(InvalidCredentialsException.class);

		verify(limiter, never()).reset(any());
	}

	@Test
	void whenTheLimitIsReachedNothingIsLookedUpAndNoPasswordIsHashed() {
		org.mockito.Mockito.lenient().doThrow(new TooManyAttemptsException(120)).when(limiter).record(eq("login-email:ann@example.com"), org.mockito.ArgumentMatchers.anyInt(), any());

		assertThatThrownBy(() -> service.login(new LoginRequest("ann@example.com", "guess"), IP)).isInstanceOf(TooManyAttemptsException.class);

		verify(users, never()).findByEmail(any());
	}

	@Test
	void registrationIsLimitedPerIp() {
		org.mockito.Mockito.lenient().doThrow(new TooManyAttemptsException(60)).when(limiter).record(eq("register-ip:" + IP), org.mockito.ArgumentMatchers.anyInt(), any());

		assertThatThrownBy(() -> service.register(new RegisterRequest("spam@example.com", "correct horse"), IP)).isInstanceOf(TooManyAttemptsException.class);

		verify(users, never()).saveAndFlush(any());
	}
}
