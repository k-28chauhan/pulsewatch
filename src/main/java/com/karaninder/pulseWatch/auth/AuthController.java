package com.karaninder.pulseWatch.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The only endpoints reachable without a token (see SecurityConfig). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService service;

	public AuthController(AuthService service) {
		this.service = service;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
		return service.register(request, http.getRemoteAddr());
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		return service.login(request, http.getRemoteAddr());
	}
}
