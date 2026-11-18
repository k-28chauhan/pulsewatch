package com.karaninder.pulseWatch.stats;

import java.time.Instant;

/** One point of the latency chart: percentiles of the successful checks in a time bucket. */
public record LatencyBucket(Instant bucketStart, Double p50, Double p95, long checks) {
}
