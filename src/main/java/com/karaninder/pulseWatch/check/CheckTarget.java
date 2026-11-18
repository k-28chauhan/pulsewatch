package com.karaninder.pulseWatch.check;

import com.karaninder.pulseWatch.monitor.Monitor;
import com.karaninder.pulseWatch.monitor.MonitorMethod;

/**
 * An immutable snapshot of exactly what a check needs from a Monitor.
 * <p>
 * Why not pass the Monitor entity to the worker threads? Entities belong to one transaction and
 * one thread. A plain record is safe to hand to any thread, and it keeps the HTTP code
 * independent of JPA (HttpChecker can be tested without a database).
 */
public record CheckTarget(
		Long monitorId,
		String url,
		MonitorMethod method,
		int expectedStatus,
		int timeoutMs,
		int intervalSeconds,   // bounds how many retries fit before the next check is due
		int failureThreshold,  // how many failed checks in a row open an incident
		Integer latencyThresholdMs // null = no "slow response" alerts for this monitor
) {

	public static CheckTarget from(Monitor m) {
		return new CheckTarget(m.getId(), m.getUrl(), m.getMethod(), m.getExpectedStatus(), m.getTimeoutMs(),
				m.getIntervalSeconds(), m.getFailureThreshold(), m.getLatencyThresholdMs());
	}
}
