package com.karaninder.pulseWatch.incident;

import java.time.Duration;
import java.time.Instant;

/**
 * @param durationSeconds length of the outage; for an open incident, how long it has lasted so far
 */
public record IncidentResponse(Long id, Long monitorId, Instant startedAt, Instant resolvedAt,
                               boolean open, long durationSeconds, String error) {

	public static IncidentResponse from(Incident incident, Instant now) {
		Instant end = incident.isOpen() ? now : incident.getResolvedAt();
		return new IncidentResponse(incident.getId(), incident.getMonitorId(), incident.getStartedAt(),
				incident.getResolvedAt(), incident.isOpen(),
				Duration.between(incident.getStartedAt(), end).toSeconds(), incident.getError());
	}
}
