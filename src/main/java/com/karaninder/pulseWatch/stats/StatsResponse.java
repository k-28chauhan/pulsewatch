package com.karaninder.pulseWatch.stats;

import com.karaninder.pulseWatch.incident.IncidentResponse;

import java.time.Instant;
import java.util.List;

/** Everything a monitor's detail page needs, in one response. */
public record StatsResponse(
		Long monitorId,
		Instant generatedAt,
		UptimeStats uptime24h,
		UptimeStats uptime7d,
		UptimeStats uptime30d,
		LatencyStats latency24h,
		LatencyStats latency7d,
		List<ErrorRateBucket> errorRate24h,
		IncidentResponse openIncident // null while the monitor is healthy
) {
}
