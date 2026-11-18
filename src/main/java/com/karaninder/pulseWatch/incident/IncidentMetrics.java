package com.karaninder.pulseWatch.incident;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * A gauge is a value that goes up AND down, read at the moment Prometheus scrapes (every few seconds):
 * the number of monitors that are down right now. The count is a cheap index lookup (the partial unique index).
 */
@Component
public class IncidentMetrics {

	public IncidentMetrics(MeterRegistry registry, IncidentRepository incidents) {
		Gauge.builder("pulsewatch.incidents.open", incidents, IncidentRepository::countByResolvedAtIsNull)
				.description("Monitors with an open incident")
				.register(registry);
	}
}
