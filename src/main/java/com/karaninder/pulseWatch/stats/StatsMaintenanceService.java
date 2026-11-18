package com.karaninder.pulseWatch.stats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The nightly housekeeping, in the one order that cannot lose data:
 * 1. summarise every complete hour into check_results_hourly,
 * 2. only then delete raw rows older than the retention (cut at a whole hour).
 * Both steps run in ONE transaction: if the delete fails, the rollup is rolled back too, and the next run starts clean.
 */
@Service
public class StatsMaintenanceService {

	private static final Logger log = LoggerFactory.getLogger(StatsMaintenanceService.class);

	public record Result(int hourlyRowsWritten, int rawRowsDeleted) {
	}

	private final StatsRepository stats;
	private final StatsProperties properties;

	public StatsMaintenanceService(StatsRepository stats, StatsProperties properties) {
		this.stats = stats;
		this.properties = properties;
	}

	@Transactional
	public Result run(Instant now) {
		Instant currentHourStart = now.truncatedTo(ChronoUnit.HOURS); // the running hour is incomplete: never summarise it
		Instant cutoff = now.minus(properties.rawRetention()).truncatedTo(ChronoUnit.HOURS);

		int written = stats.rollUp(currentHourStart);
		int deleted = stats.deleteRawBefore(cutoff);
		log.info("Stats maintenance: {} hourly rows written, {} raw rows deleted (older than {})", written, deleted, cutoff);
		return new Result(written, deleted);
	}
}
