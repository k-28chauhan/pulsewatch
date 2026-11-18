package com.karaninder.pulseWatch.auth;

/** One message for "no such user" and "wrong password", so an attacker cannot learn which emails are registered. */
public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Invalid email or password");
	}
}
