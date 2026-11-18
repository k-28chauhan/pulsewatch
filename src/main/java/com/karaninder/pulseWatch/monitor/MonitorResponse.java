package com.karaninder.pulseWatch.monitor;

import java.time.Instant;

/**
 * The JSON we send back. Separate from the entity so the API shape can stay stable
 * even if the database model changes, and so we never leak internals by accident.
 */
public record MonitorResponse(
		Long id,
		String name,
		String url,
		MonitorMethod method,
		int expectedStatus,
		int intervalSeconds,
		int timeoutMs,
		Integer latencyThresholdMs,
		int failureThreshold,
		boolean enabled,
		Instant nextCheckAt,
		Instant createdAt,
		Instant updatedAt
) {

	/** Static factory: converts an entity into its response shape. */
	public static MonitorResponse from(Monitor m) {
		return new MonitorResponse(
				m.getId(), m.getName(), m.getUrl(), m.getMethod(), m.getExpectedStatus(),
				m.getIntervalSeconds(), m.getTimeoutMs(), m.getLatencyThresholdMs(),
				m.getFailureThreshold(), m.isEnabled(), m.getNextCheckAt(),
				m.getCreatedAt(), m.getUpdatedAt());
	}
}
