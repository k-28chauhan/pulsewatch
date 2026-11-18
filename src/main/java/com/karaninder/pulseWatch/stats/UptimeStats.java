package com.karaninder.pulseWatch.stats;

/**
 * @param uptimePercent share of successful checks, 0 to 100 with three decimals; null if no check ran in the window
 * @param avgLatencyMs  average latency of successful checks; null if there were none
 */
public record UptimeStats(Double uptimePercent, long totalChecks, long failedChecks, Double avgLatencyMs) {
}
