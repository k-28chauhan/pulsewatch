package com.karaninder.pulseWatch.monitor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * The JSON a client sends to create (POST) or replace (PUT) a monitor.
 * A record is an immutable data holder: the compiler writes the constructor,
 * getters (name(), url()...), equals, hashCode and toString for us.
 * <p>
 * Only name and url are required. The compact constructor fills in defaults for the rest
 * BEFORE validation runs, so e.g. a missing intervalSeconds becomes 60 and then passes @Min(10).
 * <p>
 * @TimeoutShorterThanInterval is a rule across two fields, so it sits on the record itself.
 */
@TimeoutShorterThanInterval
public record MonitorRequest(
		@NotBlank @Size(max = 100)
		String name,

		// @URL alone also accepts ftp:// and file:// - the regexp limits it to http(s).
		@NotBlank @Size(max = 2048) @URL(regexp = "^https?://.*")
		String url,

		MonitorMethod method,

		@Min(100) @Max(599)
		Integer expectedStatus,

		@Min(10)
		Integer intervalSeconds,

		@Min(100) @Max(60_000)
		Integer timeoutMs,

		@Min(1)
		Integer latencyThresholdMs, // optional: stays null if not given

		@Min(1)
		Integer failureThreshold
) {

	public MonitorRequest {
		method = method == null ? MonitorMethod.GET : method;
		expectedStatus = expectedStatus == null ? 200 : expectedStatus;
		intervalSeconds = intervalSeconds == null ? 60 : intervalSeconds;
		timeoutMs = timeoutMs == null ? 5000 : timeoutMs;
		failureThreshold = failureThreshold == null ? 3 : failureThreshold;
	}
}
