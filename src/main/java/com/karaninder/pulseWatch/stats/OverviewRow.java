package com.karaninder.pulseWatch.stats;

import java.time.Instant;

/** The raw columns of the overview query, before the status is decided. */
record OverviewRow(Long id, String name, String url, boolean enabled, int intervalSeconds,
                   Instant lastCheckedAt, Boolean lastSuccess, Integer lastLatencyMs,
                   long totalChecks, long failedChecks, boolean hasOpenIncident) {
}
