package com.karaninder.pulseWatch.stats;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Triggers the nightly maintenance. The cron expression has six fields: second minute hour day month weekday,
 * so "0 0 3 * * *" is 03:00:00 every day, in UTC. The work is idempotent, so a missed or repeated run is harmless.
 * With several app instances, @SchedulerLock lets only one of them run it per night.
 */
@Component
@ConditionalOnProperty(name = "pulsewatch.stats.maintenance-enabled", havingValue = "true", matchIfMissing = true)
public class StatsMaintenanceJob {

	private static final Logger log = LoggerFactory.getLogger(StatsMaintenanceJob.class);

	private final StatsMaintenanceService service;
	private final Clock clock;

	public StatsMaintenanceJob(StatsMaintenanceService service, Clock clock) {
		this.service = service;
		this.clock = clock;
	}

	@Scheduled(cron = "${pulsewatch.stats.maintenance-cron:0 0 3 * * *}", zone = "UTC")
	@SchedulerLock(name = "statsMaintenance", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
	public void nightly() {
		try {
			service.run(clock.instant());
		} catch (Exception e) {
			log.error("Stats maintenance failed; it will be retried tomorrow", e); // a scheduled method must not throw
		}
	}
}
