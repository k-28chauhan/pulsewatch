package com.karaninder.pulseWatch.incident;

import java.time.Instant;

/** "A monitor is back up." Carries both timestamps so a listener can say how long the outage lasted. */
public record IncidentResolved(Long incidentId, Long monitorId, Instant startedAt, Instant resolvedAt) {
}
