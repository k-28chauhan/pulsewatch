package com.karaninder.pulseWatch.stats;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param rawRetention how long raw check_results rows are kept before the nightly job deletes them.
 *                     It must be at least 7 days, because the 24 h and 7 d latency percentiles are computed from raw rows.
 */
@ConfigurationProperties(prefix = "pulsewatch.stats")
public record StatsProperties(@DefaultValue("P7D") Duration rawRetention) {

	public StatsProperties {
		if (rawRetention.compareTo(Duration.ofDays(7)) < 0) {
			throw new IllegalArgumentException("pulsewatch.stats.raw-retention must be at least 7 days");
		}
	}
}
