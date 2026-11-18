package com.karaninder.pulseWatch.check;

import com.karaninder.pulseWatch.incident.IncidentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Runs ONE check end to end: call the URL (with retries), save the result, update the incident state,
 * and announce a slow response. Deliberately NOT @Transactional: the slow part (network calls and
 * backoff sleeps) must never hold a database connection open. Each repository/service call below
 * opens its own short transaction.
 */
@Service
public class CheckRunner {

	private static final Logger log = LoggerFactory.getLogger(CheckRunner.class);

	private final RetryingChecker checker;
	private final CheckResultRepository repository;
	private final IncidentService incidentService;
	private final ApplicationEventPublisher events;
	private final CheckMetrics metrics;

	public CheckRunner(RetryingChecker checker, CheckResultRepository repository,
	                   IncidentService incidentService, ApplicationEventPublisher events, CheckMetrics metrics) {
		this.checker = checker;
		this.repository = repository;
		this.incidentService = incidentService;
		this.events = events;
		this.metrics = metrics;
	}

	public CheckResult run(CheckTarget target) {
		Instant checkedAt = Instant.now(); // when the check STARTED, not when the row was saved
		RetriedOutcome retried = checker.check(target);
		CheckResult saved = repository.save(new CheckResult(target.monitorId(), checkedAt, retried.outcome(), retried.attempts()));

		// The result is committed first; the incident logic then reads it back from the database.
		try {
			incidentService.evaluate(saved, target.failureThreshold());
		} catch (DataIntegrityViolationException e) {
			// The partial unique index refused a second open incident: someone else opened it first.
			// The service's transaction rolled back cleanly, and the database is correct, so this is not an error.
			log.info("Monitor {}: an incident is already open (unique index prevented a duplicate)", target.monitorId());
		}

		announceIfSlow(target, saved);
		metrics.record(saved);
		return saved;
	}

	/** Only a SUCCESSFUL but slow check counts: a failed check is already covered by the incident logic. */
	private void announceIfSlow(CheckTarget target, CheckResult result) {
		Integer threshold = target.latencyThresholdMs();
		if (result.isSuccess() && threshold != null && result.getLatencyMs() > threshold) {
			events.publishEvent(new SlowResponseDetected(
					target.monitorId(), result.getLatencyMs(), threshold, result.getCheckedAt()));
		}
	}
}
