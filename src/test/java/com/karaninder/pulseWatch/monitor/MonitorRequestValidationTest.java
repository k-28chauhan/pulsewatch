package com.karaninder.pulseWatch.monitor;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the validation rules on their own, with the real Bean Validation engine but no Spring.
 * Validation.buildDefaultValidatorFactory() is what Spring uses under the hood.
 */
class MonitorRequestValidationTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	private MonitorRequest request(Integer intervalSeconds, Integer timeoutMs) {
		return new MonitorRequest("Homepage", "https://example.com", null, null, intervalSeconds, timeoutMs, null, null);
	}

	@Test
	void defaultsAreValid() {
		assertThat(validator.validate(request(null, null))).isEmpty();
	}

	@Test
	void timeoutEqualToIntervalIsRejectedAndReportedOnTheTimeoutField() {
		Set<ConstraintViolation<MonitorRequest>> violations = validator.validate(request(10, 10_000));

		assertThat(violations).hasSize(1);
		ConstraintViolation<MonitorRequest> violation = violations.iterator().next();
		assertThat(violation.getPropertyPath().toString()).isEqualTo("timeoutMs");
		assertThat(violation.getMessage()).isEqualTo("must be shorter than the check interval (10000 ms)");
	}

	@Test
	void timeoutLongerThanIntervalIsRejected() {
		assertThat(validator.validate(request(10, 60_000))).hasSize(1);
	}

	@Test
	void timeoutJustBelowTheIntervalIsAccepted() {
		assertThat(validator.validate(request(10, 9_999))).isEmpty();
	}

	@Test
	void aLongIntervalAllowsALongTimeout() {
		assertThat(validator.validate(request(120, 60_000))).isEmpty();
	}

	@Test
	void theSmallestAllowedIntervalStillWorksWithTheDefaultTimeout() {
		// default timeout is 5000 ms; the smallest allowed interval (10 s) is comfortably above it
		assertThat(validator.validate(request(10, null))).isEmpty();
	}
}
