package com.karaninder.pulseWatch.alert;

import com.karaninder.pulseWatch.check.SlowResponseDetected;
import com.karaninder.pulseWatch.incident.IncidentOpened;
import com.karaninder.pulseWatch.incident.IncidentResolved;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The glue between events and AlertService. It only receives events; the logic lives in the service.
 * <ul>
 *   <li>@Async("alertExecutor"): the handler runs on another thread, so a slow mail server never delays the check.</li>
 *   <li>@TransactionalEventListener(AFTER_COMMIT): for incident events, wait until the incident row is
 *       really committed. If the transaction rolls back, the event is dropped and nobody is told about
 *       an incident that does not exist.</li>
 * </ul>
 */
@Component
public class AlertListener {

	private final AlertService alertService;

	public AlertListener(AlertService alertService) {
		this.alertService = alertService;
	}

	@Async("alertExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void incidentOpened(IncidentOpened event) {
		alertService.onIncidentOpened(event);
	}

	@Async("alertExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void incidentResolved(IncidentResolved event) {
		alertService.onIncidentResolved(event);
	}

	/** Published by CheckRunner outside any transaction, so a plain @EventListener is the right tool. */
	@Async("alertExecutor")
	@EventListener
	public void slowResponse(SlowResponseDetected event) {
		alertService.onSlowResponse(event);
	}
}
