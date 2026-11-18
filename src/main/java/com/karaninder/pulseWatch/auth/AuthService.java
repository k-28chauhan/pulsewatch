package com.karaninder.pulseWatch.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AuthService {

	static final Duration WINDOW = Duration.ofMinutes(5);
	static final int LOGINS_PER_EMAIL = 10;   // guessing one account's password
	static final int LOGINS_PER_IP = 60;      // guessing many accounts from one place
	static final int REGISTRATIONS_PER_IP = 20;

	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AttemptLimiter limiter;
	private final String dummyHash;

	public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, AttemptLimiter limiter) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.limiter = limiter;
		// A real hash of a random string: login compares against it when the email is unknown, so an unknown
		// email takes as long as a wrong password (otherwise response time would reveal which emails exist).
		this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	public AuthResponse register(RegisterRequest request, String clientIp) {
		limiter.record("register-ip:" + clientIp, REGISTRATIONS_PER_IP, WINDOW);
		String email = normalise(request.email());
		if (users.existsByEmail(email)) {
			throw new EmailAlreadyRegisteredException();
		}
		try {
			User user = users.saveAndFlush(new User(email, passwordEncoder.encode(request.password())));
			return jwtService.issueFor(user);
		} catch (DataIntegrityViolationException e) {
			// Two registrations for the same email raced past existsByEmail: the unique index on lower(email) caught it.
			throw new EmailAlreadyRegisteredException();
		}
	}

	@Transactional(readOnly = true)
	public AuthResponse login(LoginRequest request, String clientIp) {
		String email = normalise(request.email());
		limiter.record("login-ip:" + clientIp, LOGINS_PER_IP, WINDOW);
		limiter.record("login-email:" + email, LOGINS_PER_EMAIL, WINDOW);
		Optional<User> user = users.findByEmail(email);
		String hash = user.map(User::getPasswordHash).orElse(dummyHash);
		boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
		if (user.isEmpty() || !passwordMatches) {
			throw new InvalidCredentialsException();
		}
		limiter.reset("login-email:" + email); // a successful login clears the failures counted against this account
		return jwtService.issueFor(user.get());
	}

	private static String normalise(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
