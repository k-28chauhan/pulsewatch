package com.karaninder.pulseWatch.stats;

import java.time.Instant;

/** One point of the error-rate chart. Buckets that contain no checks are simply absent. */
public record ErrorRateBucket(Instant bucketStart, long totalChecks, long failedChecks, double errorRate) {

	public static ErrorRateBucket of(Instant bucketStart, long totalChecks, long failedChecks) {
		double rate = totalChecks == 0 ? 0.0 : (double) failedChecks / totalChecks;
		return new ErrorRateBucket(bucketStart, totalChecks, failedChecks, rate);
	}
}
