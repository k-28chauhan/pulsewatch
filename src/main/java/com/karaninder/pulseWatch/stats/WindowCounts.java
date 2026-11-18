package com.karaninder.pulseWatch.stats;

/** Raw numbers for one time window: how many checks ran, how many failed, and the latency of the successful ones. */
public record WindowCounts(long totalChecks, long failedChecks, long latencySumMs) {

	public long successfulChecks() {
		return totalChecks - failedChecks;
	}
}
