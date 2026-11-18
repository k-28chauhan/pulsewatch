package com.karaninder.pulseWatch.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The password is limited to 72 characters because BCrypt only looks at the first 72 bytes. */
public record RegisterRequest(
		@NotBlank @Email @Size(max = 254)
		String email,

		@NotBlank @Size(min = 8, max = 72)
		String password
) {
}
