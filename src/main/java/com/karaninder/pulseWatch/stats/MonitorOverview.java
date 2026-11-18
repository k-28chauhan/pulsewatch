package com.karaninder.pulseWatch.stats;

import java.time.Instant;

/** One row of the monitor list: what the user needs to see at a glance, in a single query for all monitors. */
public record MonitorOverview(
		Long id,
		String name,
		String url,
		MonitorStatus status,
		boolean enabled,
		int intervalSeconds,
		Instant lastCheckedAt,   // null until the first check
		Integer lastLatencyMs,   // latency of the last check if it succeeded, else null
		Double uptime24h         // null when no check ran in the last 24 h
) {
}
