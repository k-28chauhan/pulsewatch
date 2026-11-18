package com.karaninder.pulseWatch.check;

import java.time.Duration;

/**
 * The rules of retrying, with no I/O and no sleeping, so every rule can be tested instantly.
 *
 * @param maxAttempts    total tries per check, including the first one (1 = never retry)
 * @param initialBackoff the wait before the 2nd attempt; it doubles before each later one
 */
public record RetryPolicy(int maxAttempts, Duration initialBackoff) {

	public RetryPolicy {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts must be at least 1");
		}
		if (initialBackoff.isNegative()) {
			throw new IllegalArgumentException("initialBackoff must not be negative");
		}
	}

	/** Exponential backoff: attempt 2 waits 1x, attempt 3 waits 2x, attempt 4 waits 4x... */
	public Duration backoffBefore(int attemptNumber) {
		if (attemptNumber < 2) {
			throw new IllegalArgumentException("the first attempt has no backoff");
		}
		return initialBackoff.multipliedBy(1L << (attemptNumber - 2)); // 1L << n is 2 to the power n
	}

	/**
	 * How many attempts can run in the WORST case (every attempt hits its timeout) and still finish
	 * before the monitor's next check is due? Without this, retries would reintroduce the overlapping
	 * checks that the "timeout shorter than interval" rule prevents. The first attempt is always allowed.
	 */
	public int attemptsThatFit(int timeoutMs, long intervalMs) {
		int attempts = 1;
		long worstCaseMs = timeoutMs;
		while (attempts < maxAttempts) {
			long next = worstCaseMs + backoffBefore(attempts + 1).toMillis() + timeoutMs;
			if (next >= intervalMs) {
				break;
			}
			worstCaseMs = next;
			attempts++;
		}
		return attempts;
	}
}
