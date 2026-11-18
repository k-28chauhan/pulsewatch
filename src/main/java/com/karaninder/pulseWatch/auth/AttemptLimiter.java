package com.karaninder.pulseWatch.auth;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A sliding-window rate limiter: "at most N attempts per key in any window of length W".
 * Slows down password guessing and account spamming. It keeps its counters in memory, so each app instance
 * counts separately (with k instances an attacker gets at most k times the limit). That is acceptable here; a shared
 * counter (Redis, or a database table) would be the next step. Behind a reverse proxy the client IP comes from
 * X-Forwarded-For, which Spring only trusts when server.forward-headers-strategy=framework (set in the prod compose file).
 */
@Component
public class AttemptLimiter {

	private static final int CLEANUP_THRESHOLD = 10_000;
	private static final Duration STALE_AFTER = Duration.ofHours(1);

	private final Clock clock;
	private final ConcurrentHashMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

	public AttemptLimiter(Clock clock) {
		this.clock = clock;
	}

	/**
	 * Counts one attempt for {@code key}, or throws if the key already used up its {@code maxAttempts}
	 * within the last {@code window}. Refused attempts are not counted, so the lock-out always ends
	 * one window after the oldest counted attempt.
	 */
	public void record(String key, int maxAttempts, Duration window) {
		Instant now = clock.instant();
		Deque<Instant> times = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
		synchronized (times) {
			Instant cutoff = now.minus(window);
			while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
				times.pollFirst(); // fell out of the window
			}
			if (times.size() >= maxAttempts) {
				long wait = Duration.between(now, times.peekFirst().plus(window)).toSeconds() + 1;
				throw new TooManyAttemptsException(Math.max(wait, 1));
			}
			times.addLast(now);
		}
		if (attempts.size() > CLEANUP_THRESHOLD) {
			removeStaleKeys(now);
		}
	}

	/** Forget a key, e.g. after a successful login. */
	public void reset(String key) {
		attempts.remove(key);
	}

	private void removeStaleKeys(Instant now) {
		Instant cutoff = now.minus(STALE_AFTER);
		attempts.entrySet().removeIf(entry -> {
			synchronized (entry.getValue()) {
				return entry.getValue().isEmpty() || entry.getValue().peekLast().isBefore(cutoff);
			}
		});
	}
}
