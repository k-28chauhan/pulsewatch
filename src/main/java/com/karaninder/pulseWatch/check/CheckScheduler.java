package com.karaninder.pulseWatch.check;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The heartbeat. Every tick: claim the due monitors, then hand each one to its own virtual thread.
 * The tick itself does almost no work, so it always finishes quickly and never falls behind.
 */
@Component
@ConditionalOnProperty(name = "pulsewatch.check.enabled", havingValue = "true", matchIfMissing = true)
public class CheckScheduler {

	private static final Logger log = LoggerFactory.getLogger(CheckScheduler.class);

	private final CheckScheduleService scheduleService;
	private final CheckRunner runner;
	private final Executor executor;

	public CheckScheduler(CheckScheduleService scheduleService, CheckRunner runner,
	                      @Qualifier("checkExecutor") Executor executor) {
		this.scheduleService = scheduleService;
		this.runner = runner;
		this.executor = executor;
	}

	// fixedDelay: wait 10 s AFTER the previous tick finished, so ticks can never overlap.
	// The default can be overridden with the property pulsewatch.check.tick-delay (ISO-8601, e.g. PT2S).
	// @SchedulerLock: with several app instances, only the one that takes the "checkScheduler.tick" row in the
	// shedlock table runs this round. That serialises the claim step, so two instances never claim the same monitor.
	// lockAtMostFor: if the winner dies mid-tick, the lock frees itself after 30 s (a normal tick takes milliseconds).
	@Scheduled(fixedDelayString = "${pulsewatch.check.tick-delay:PT10S}")
	@SchedulerLock(name = "checkScheduler.tick", lockAtMostFor = "PT30S", lockAtLeastFor = "PT1S")
	public void tick() {
		List<CheckTarget> due = scheduleService.claimDueMonitors(Instant.now());
		if (!due.isEmpty()) {
			log.info("Tick: {} monitor(s) due", due.size());
		}
		for (CheckTarget target : due) {
			executor.execute(() -> runSafely(target));
		}
	}

	/** Anything thrown inside a worker thread would vanish silently, so catch and log it here. */
	private void runSafely(CheckTarget target) {
		try {
			CheckResult result = runner.run(target);
			log.info("Monitor {} -> {} in {} ms{}", target.monitorId(),
					result.isSuccess() ? "UP" : "DOWN", result.getLatencyMs(),
					result.isSuccess() ? "" : " (" + result.getError() + ")");
		} catch (Exception e) {
			// e.g. the monitor was deleted between claiming and saving (foreign key violation)
			log.error("Check for monitor {} failed to complete", target.monitorId(), e);
		}
	}
}
