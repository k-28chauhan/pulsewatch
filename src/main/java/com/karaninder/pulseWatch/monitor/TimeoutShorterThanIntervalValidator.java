package com.karaninder.pulseWatch.monitor;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Rule: timeoutMs < intervalSeconds * 1000.
 * Why: a check may run for up to timeoutMs. If that is longer than the interval, the next check
 * for the same monitor starts while the previous one is still running (overlapping checks).
 */
public class TimeoutShorterThanIntervalValidator
		implements ConstraintValidator<TimeoutShorterThanInterval, MonitorRequest> {

	@Override
	public boolean isValid(MonitorRequest request, ConstraintValidatorContext context) {
		// null means "not our job": other annotations (@Min...) report missing values.
		if (request == null || request.timeoutMs() == null || request.intervalSeconds() == null) {
			return true;
		}
		long intervalMs = request.intervalSeconds() * 1000L; // 1000L (long) avoids int overflow
		if (request.timeoutMs() < intervalMs) {
			return true;
		}

		// By default a class-level violation belongs to the whole object. Re-attach it to the
		// timeoutMs field so the API error reads {"errors": {"timeoutMs": "..."}}.
		context.disableDefaultConstraintViolation();
		context.buildConstraintViolationWithTemplate(
						"must be shorter than the check interval (" + intervalMs + " ms)")
				.addPropertyNode("timeoutMs")
				.addConstraintViolation();
		return false;
	}
}
