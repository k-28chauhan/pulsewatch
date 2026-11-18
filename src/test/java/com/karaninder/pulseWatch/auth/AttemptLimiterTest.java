package com.karaninder.pulseWatch.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptLimiterTest {

	/** A clock the test can move forward by hand. */
	private static class ManualClock extends Clock {
		private Instant now = Instant.parse("2026-11-10T12:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	private static final Duration WINDOW = Duration.ofMinutes(5);

	private final ManualClock clock = new ManualClock();
	private final AttemptLimiter limiter = new AttemptLimiter(clock);

	@Test
	void allowsUpToTheLimitThenRefuses() {
		for (int i = 0; i < 3; i++) {
			limiter.record("a", 3, WINDOW);
		}

		assertThatThrownBy(() -> limiter.record("a", 3, WINDOW)).isInstanceOf(TooManyAttemptsException.class);
	}

	@Test
	void tellsTheCallerHowLongToWait() {
		limiter.record("a", 1, WINDOW);
		clock.advance(Duration.ofMinutes(2));

		assertThatThrownBy(() -> limiter.record("a", 1, WINDOW))
				.isInstanceOfSatisfying(TooManyAttemptsException.class, e -> assertThat(e.getRetryAfterSeconds()).isBetween(179L, 181L)); // 3 minutes left
	}

	@Test
	void theWindowSlidesSoOldAttemptsStopCounting() {
		limiter.record("a", 2, WINDOW);
		clock.advance(Duration.ofMinutes(3));
		limiter.record("a", 2, WINDOW);
		clock.advance(Duration.ofMinutes(3)); // the first attempt is now 6 minutes old: out of the window

		assertThatCode(() -> limiter.record("a", 2, WINDOW)).doesNotThrowAnyException();
		assertThatThrownBy(() -> limiter.record("a", 2, WINDOW)).isInstanceOf(TooManyAttemptsException.class);
	}

	@Test
	void keysAreIndependent() {
		limiter.record("a", 1, WINDOW);

		assertThatCode(() -> limiter.record("b", 1, WINDOW)).doesNotThrowAnyException();
	}

	@Test
	void refusedAttemptsAreNotCountedSoTheLockEndsOnSchedule() {
		limiter.record("a", 1, WINDOW);
		for (int i = 0; i < 20; i++) {
			assertThatThrownBy(() -> limiter.record("a", 1, WINDOW)).isInstanceOf(TooManyAttemptsException.class);
		}
		clock.advance(WINDOW.plusSeconds(1));

		assertThatCode(() -> limiter.record("a", 1, WINDOW)).doesNotThrowAnyException(); // 20 refused tries did not extend the lock
	}

	@Test
	void resetForgetsAKey() {
		limiter.record("a", 1, WINDOW);
		limiter.reset("a");

		assertThatCode(() -> limiter.record("a", 1, WINDOW)).doesNotThrowAnyException();
	}
}
