package com.karaninder.pulseWatch.check;

import com.karaninder.pulseWatch.monitor.MonitorMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryingCheckerTest {

	@Mock
	HttpChecker httpChecker;

	private final List<Duration> sleeps = new ArrayList<>(); // a fake Sleeper records instead of waiting
	private final Sleeper recordingSleeper = sleeps::add;
	private final RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(500));

	private final CheckOutcome up = CheckOutcome.responded(200, 20, 200);
	private final CheckOutcome down = CheckOutcome.responded(500, 20, 200);

	private CheckTarget target(int timeoutMs, int intervalSeconds) {
		return new CheckTarget(1L, "https://example.com", MonitorMethod.GET, 200, timeoutMs, intervalSeconds, 3, null);
	}

	private RetryingChecker checker(Sleeper sleeper) {
		return new RetryingChecker(httpChecker, policy, sleeper);
	}

	@Test
	void aSuccessOnTheFirstTryIsNotRetriedAndNeverSleeps() {
		CheckTarget target = target(5000, 60);
		when(httpChecker.check(target)).thenReturn(up);

		RetriedOutcome result = checker(recordingSleeper).check(target);

		assertThat(result.attempts()).isEqualTo(1);
		assertThat(result.outcome().success()).isTrue();
		assertThat(sleeps).isEmpty();
	}

	@Test
	void aBlipThatRecoversOnTheSecondTryCountsAsUp() {
		CheckTarget target = target(5000, 60);
		when(httpChecker.check(target)).thenReturn(down, up);

		RetriedOutcome result = checker(recordingSleeper).check(target);

		assertThat(result.attempts()).isEqualTo(2);
		assertThat(result.outcome().success()).isTrue();
		assertThat(sleeps).containsExactly(Duration.ofMillis(500));
	}

	@Test
	void everyAttemptFailingGivesUpAfterMaxAttemptsWithGrowingWaits() {
		CheckTarget target = target(5000, 60);
		when(httpChecker.check(target)).thenReturn(down);

		RetriedOutcome result = checker(recordingSleeper).check(target);

		assertThat(result.attempts()).isEqualTo(3);
		assertThat(result.outcome().success()).isFalse();
		assertThat(sleeps).containsExactly(Duration.ofMillis(500), Duration.ofMillis(1000));
		verify(httpChecker, times(3)).check(target);
	}

	@Test
	void aShortIntervalLimitsTheRetriesSoChecksCannotOverlap() {
		CheckTarget target = target(5000, 10); // only 1 attempt fits in a 10 s interval
		when(httpChecker.check(target)).thenReturn(down);

		RetriedOutcome result = checker(recordingSleeper).check(target);

		assertThat(result.attempts()).isEqualTo(1);
		assertThat(sleeps).isEmpty();
	}

	@Test
	void interruptionDuringBackoffStopsRetryingAndKeepsTheInterruptFlag() {
		CheckTarget target = target(5000, 60);
		when(httpChecker.check(target)).thenReturn(down);
		Sleeper interrupted = duration -> {
			throw new InterruptedException();
		};

		try {
			RetriedOutcome result = checker(interrupted).check(target);

			assertThat(result.attempts()).isEqualTo(1);
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		} finally {
			Thread.interrupted(); // clear the flag so it cannot leak into other tests
		}
	}
}
