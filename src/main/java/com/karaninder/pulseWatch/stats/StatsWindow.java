package com.karaninder.pulseWatch.stats;

import java.time.Duration;
import java.util.Arrays;

/** The time ranges the API offers, each with the chart bucket size that suits it. */
public enum StatsWindow {

	LAST_24H("24h", Duration.ofHours(24), Duration.ofHours(1), Duration.ofMinutes(15)), // 24 error buckets, 96 latency points
	LAST_7D("7d", Duration.ofDays(7), Duration.ofHours(6), Duration.ofHours(1)),        // 28 error buckets, 168 latency points
	LAST_30D("30d", Duration.ofDays(30), Duration.ofDays(1), null);                     // no latency series (raw rows are kept 7 days)

	private final String label;
	private final Duration length;
	private final Duration bucket;
	private final Duration latencyBucket;

	StatsWindow(String label, Duration length, Duration bucket, Duration latencyBucket) {
		this.label = label;
		this.length = length;
		this.bucket = bucket;
		this.latencyBucket = latencyBucket;
	}

	/** Bucket size of the latency chart, or null if this window has no latency series. */
	public Duration latencyBucket() {
		return latencyBucket;
	}

	public String label() {
		return label;
	}

	public Duration length() {
		return length;
	}

	public Duration bucket() {
		return bucket;
	}

	/** "24h" becomes LAST_24H. Unknown text throws InvalidStatsWindowException.unknown (GlobalExceptionHandler turns it into a 400). */
	public static StatsWindow fromLabel(String label) {
		return Arrays.stream(values())
				.filter(window -> window.label.equals(label))
				.findFirst()
				.orElseThrow(() -> InvalidStatsWindowException.unknown(label));
	}
}
