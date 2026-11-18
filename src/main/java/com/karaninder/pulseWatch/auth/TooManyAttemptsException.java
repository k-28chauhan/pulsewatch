package com.karaninder.pulseWatch.auth;

/** Too many tries in a short time. Answered with HTTP 429 and a Retry-After header. */
public class TooManyAttemptsException extends RuntimeException {

	private final long retryAfterSeconds;

	public TooManyAttemptsException(long retryAfterSeconds) {
		super("Too many attempts. Try again in " + retryAfterSeconds + " seconds.");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
