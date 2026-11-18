package com.karaninder.pulseWatch.stats;

/** Latency of SUCCESSFUL checks in milliseconds. Every field is null when the window has no successful check. */
public record LatencyStats(Double p50, Double p95, Double p99, Integer max) {
}
