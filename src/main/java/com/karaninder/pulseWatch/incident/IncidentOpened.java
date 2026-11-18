package com.karaninder.pulseWatch.incident;

import java.time.Instant;

/**
 * "A monitor just went down." A plain immutable fact about something that already happened,
 * so it is named in the past tense. The incident package publishes it and does not know who listens.
 */
public record IncidentOpened(Long incidentId, Long monitorId, Instant startedAt, String error) {
}
