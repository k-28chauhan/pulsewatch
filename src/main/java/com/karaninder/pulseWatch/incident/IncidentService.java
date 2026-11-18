package com.karaninder.pulseWatch.incident;

import com.karaninder.pulseWatch.check.CheckResult;
import com.karaninder.pulseWatch.check.CheckResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Decides, after every finished check, whether an incident opens, stays open or resolves.
 * <pre>
 *   no incident --(failureThreshold failures in a row)--> OPEN --(first success)--> RESOLVED
 *   OPEN + another failure: nothing happens (this is the deduplication)
 * </pre>
 * After each real change it publishes an event (IncidentOpened / IncidentResolved). This class does not
 * know who listens: the alert package does, and it only reacts after this transaction has committed.
 */
@Service
@Transactional
public class IncidentService {

	private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

	private final IncidentRepository incidents;
	private final CheckResultRepository results;
	private final ApplicationEventPublisher events;

	public IncidentService(IncidentRepository incidents, CheckResultRepository results, ApplicationEventPublisher events) {
		this.incidents = incidents;
		this.results = results;
		this.events = events;
	}

	/**
	 * @param latest           the check that just finished (already saved)
	 * @param failureThreshold how many failed checks in a row open an incident
	 */
	public IncidentTransition evaluate(CheckResult latest, int failureThreshold) {
		Optional<Incident> open = incidents.findByMonitorIdAndResolvedAtIsNull(latest.getMonitorId());

		if (latest.isSuccess()) {
			return open.map(incident -> resolve(incident, latest)).orElse(IncidentTransition.NONE);
		}
		if (open.isPresent()) {
			return IncidentTransition.NONE; // already known to be down: do NOT open a second incident
		}
		return openIfThresholdReached(latest, failureThreshold);
	}

	private IncidentTransition resolve(Incident incident, CheckResult latest) {
		incident.resolve(latest.getCheckedAt()); // no save(): the entity is managed, dirty checking writes it
		log.info("Incident {} resolved for monitor {}", incident.getId(), incident.getMonitorId());
		// Listeners marked AFTER_COMMIT only run if this transaction really commits.
		events.publishEvent(new IncidentResolved(incident.getId(), incident.getMonitorId(),
				incident.getStartedAt(), incident.getResolvedAt()));
		return IncidentTransition.RESOLVED;
	}

	private IncidentTransition openIfThresholdReached(CheckResult latest, int failureThreshold) {
		// The newest N results, newest first. "N failures in a row" means all N rows failed.
		List<CheckResult> recent = results.findByMonitorIdOrderByCheckedAtDesc(
				latest.getMonitorId(), PageRequest.of(0, failureThreshold));

		boolean enoughHistory = recent.size() == failureThreshold;
		boolean allFailed = recent.stream().noneMatch(CheckResult::isSuccess);
		if (!enoughHistory || !allFailed) {
			return IncidentTransition.NONE;
		}

		CheckResult firstFailure = recent.get(recent.size() - 1); // the oldest of the streak
		// saveAndFlush sends the INSERT now, so a unique-index violation surfaces inside this method.
		Incident incident = incidents.saveAndFlush(
				Incident.open(latest.getMonitorId(), firstFailure.getCheckedAt(), latest.getError()));
		log.warn("Incident opened for monitor {} after {} failed checks: {}",
				latest.getMonitorId(), failureThreshold, latest.getError());
		events.publishEvent(new IncidentOpened(incident.getId(), incident.getMonitorId(),
				incident.getStartedAt(), incident.getError()));
		return IncidentTransition.OPENED;
	}
}
