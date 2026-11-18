package com.karaninder.pulseWatch.alert;

import com.karaninder.pulseWatch.auth.UserRepository;
import com.karaninder.pulseWatch.check.SlowResponseDetected;
import com.karaninder.pulseWatch.incident.IncidentOpened;
import com.karaninder.pulseWatch.incident.IncidentResolved;
import com.karaninder.pulseWatch.monitor.Monitor;
import com.karaninder.pulseWatch.monitor.MonitorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Turns domain events into alerts and delivers them through every enabled Notifier.
 * <ul>
 *   <li>Down / recovered alerts: sent once per incident, because the incident logic only publishes
 *       those events once per state change (deduplication lives there, not here).</li>
 *   <li>Latency alerts: repeat for as long as a monitor is slow, so they are rate-limited by a cooldown.</li>
 * </ul>
 * Deliberately NOT @Transactional: sending an email must not hold a database connection open.
 * One notifier failing never stops the others; every attempt is recorded in alert_events.
 */
@Service
public class AlertService {

	private static final Logger log = LoggerFactory.getLogger(AlertService.class);
	private static final int MAX_ERROR_LENGTH = 500; // the DB column is VARCHAR(1000)

	private final List<Notifier> notifiers; // Spring injects EVERY Notifier bean here
	private final AlertEventRepository auditLog;
	private final MonitorRepository monitors;
	private final UserRepository users;
	private final AlertProperties properties;
	private final Clock clock;
	private final MeterRegistry meters;

	public AlertService(List<Notifier> notifiers, AlertEventRepository auditLog, MonitorRepository monitors,
	                    UserRepository users, AlertProperties properties, Clock clock, MeterRegistry meters) {
		this.notifiers = notifiers;
		this.auditLog = auditLog;
		this.monitors = monitors;
		this.users = users;
		this.properties = properties;
		this.clock = clock;
		this.meters = meters;
	}

	public void onIncidentOpened(IncidentOpened event) {
		target(event.monitorId()).ifPresent(t -> dispatch(new AlertMessage(
				AlertType.INCIDENT_OPENED, t.monitor().getId(), event.incidentId(), t.ownerEmail(),
				"[PulseWatch] DOWN: " + t.monitor().getName(),
				"Monitor: " + t.monitor().getName() + "\n"
						+ "URL: " + t.monitor().getUrl() + "\n"
						+ "Down since: " + event.startedAt() + "\n"
						+ "Error: " + event.error())));
	}

	public void onIncidentResolved(IncidentResolved event) {
		target(event.monitorId()).ifPresent(t -> dispatch(new AlertMessage(
				AlertType.INCIDENT_RESOLVED, t.monitor().getId(), event.incidentId(), t.ownerEmail(),
				"[PulseWatch] RECOVERED: " + t.monitor().getName(),
				"Monitor: " + t.monitor().getName() + "\n"
						+ "URL: " + t.monitor().getUrl() + "\n"
						+ "Back up at: " + event.resolvedAt() + "\n"
						+ "Outage lasted: " + describe(Duration.between(event.startedAt(), event.resolvedAt())))));
	}

	public void onSlowResponse(SlowResponseDetected event) {
		Instant now = clock.instant();
		Instant windowStart = now.minus(properties.cooldown());
		if (auditLog.existsByMonitorIdAndTypeAndStatusAndCreatedAtAfter(
				event.monitorId(), AlertType.LATENCY_EXCEEDED, AlertStatus.SENT, windowStart)) {
			log.debug("Monitor {}: latency alert suppressed by the cooldown", event.monitorId());
			return;
		}
		target(event.monitorId()).ifPresent(t -> dispatch(new AlertMessage(
				AlertType.LATENCY_EXCEEDED, t.monitor().getId(), null, t.ownerEmail(),
				"[PulseWatch] SLOW: " + t.monitor().getName(),
				"Monitor: " + t.monitor().getName() + "\n"
						+ "URL: " + t.monitor().getUrl() + "\n"
						+ "Response time: " + event.latencyMs() + " ms (threshold " + event.thresholdMs() + " ms)\n"
						+ "Checked at: " + event.checkedAt())));
	}

	/** The monitor plus the email of its owner (the alert recipient). */
	private record Target(Monitor monitor, String ownerEmail) {
	}

	private Optional<Target> target(Long monitorId) {
		Optional<Target> target = monitors.findById(monitorId).flatMap(monitor ->
				users.findById(monitor.getUserId()).map(owner -> new Target(monitor, owner.getEmail())));
		if (target.isEmpty()) {
			log.info("Monitor {} no longer exists; alert skipped", monitorId); // deleted after the event was published
		}
		return target;
	}

	private void dispatch(AlertMessage message) {
		for (Notifier notifier : notifiers) {
			if (!notifier.isEnabled()) {
				continue;
			}
			try {
				notifier.send(message);
				record(message, notifier, AlertStatus.SENT, null);
			} catch (RuntimeException e) { // one broken channel must not stop the others
				log.warn("{} alert via {} failed: {}", message.type(), notifier.channel(), e.getMessage());
				record(message, notifier, AlertStatus.FAILED, truncate(e.getMessage()));
			}
		}
	}

	private void record(AlertMessage message, Notifier notifier, AlertStatus status, String error) {
		auditLog.save(new AlertEvent(message, notifier.channel(), status, error, clock.instant()));
		// Tags have a handful of possible values (never a monitor id), so the number of time series stays small.
		meters.counter("pulsewatch.alerts", "type", message.type().name(),
				"channel", notifier.channel().name(), "status", status.name()).increment();
	}

	private static String truncate(String text) {
		if (text == null) {
			return null;
		}
		return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
	}

	/** 252 seconds becomes "4m 12s". */
	static String describe(Duration duration) {
		long totalSeconds = duration.toSeconds();
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		if (hours > 0) {
			return hours + "h " + minutes + "m " + seconds + "s";
		}
		if (minutes > 0) {
			return minutes + "m " + seconds + "s";
		}
		return seconds + "s";
	}
}
