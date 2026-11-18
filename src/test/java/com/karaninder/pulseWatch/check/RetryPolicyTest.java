package com.karaninder.pulseWatch.check;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

	private final RetryPolicy policy = new RetryPolicy(3, Duration.ofMillis(500));

	@Test
	void backoffDoublesBeforeEachLaterAttempt() {
		assertThat(policy.backoffBefore(2)).isEqualTo(Duration.ofMillis(500));
		assertThat(policy.backoffBefore(3)).isEqualTo(Duration.ofMillis(1000));
		assertThat(policy.backoffBefore(4)).isEqualTo(Duration.ofMillis(2000));
	}

	@Test
	void theFirstAttemptHasNoBackoff() {
		assertThatThrownBy(() -> policy.backoffBefore(1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void allAttemptsFitWhenTheIntervalIsLong() {
		// worst case: 3 x 5000 + 500 + 1000 = 16 500 ms, far below 60 000 ms
		assertThat(policy.attemptsThatFit(5000, 60_000)).isEqualTo(3);
	}

	@Test
	void retriesAreCutBackSoTheyNeverRunIntoTheNextCheck() {
		// 2 attempts: 5000 + 500 + 5000 = 10 500 ms, which is not below 10 000 ms -> only the first attempt
		assertThat(policy.attemptsThatFit(5000, 10_000)).isEqualTo(1);
		// 3 attempts would need 16 500 ms; 2 attempts need 10 500 ms, which fits in 15 000 ms
		assertThat(policy.attemptsThatFit(5000, 15_000)).isEqualTo(2);
	}

	@Test
	void theFirstAttemptIsAlwaysAllowed() {
		assertThat(policy.attemptsThatFit(5000, 5001)).isEqualTo(1);
	}

	@Test
	void aPolicyWithOneAttemptNeverRetries() {
		assertThat(new RetryPolicy(1, Duration.ZERO).attemptsThatFit(100, 3_600_000)).isEqualTo(1);
	}

	@Test
	void invalidPoliciesAreRejected() {
		assertThatThrownBy(() -> new RetryPolicy(0, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new RetryPolicy(3, Duration.ofMillis(-1))).isInstanceOf(IllegalArgumentException.class);
	}
}
