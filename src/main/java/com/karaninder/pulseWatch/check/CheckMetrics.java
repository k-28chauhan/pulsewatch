package com.karaninder.pulseWatch.check;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Numbers about the check engine itself, for Prometheus and Grafana.
 * <p>
 * Tags are deliberately limited to result=up|down. A "monitor id" tag would create one time series per
 * monitor, and the number of series (not the number of requests) is what overloads a metrics system.
 * Per-monitor numbers belong in the stats API, which reads them from the database.
 */
@Component
public class CheckMetrics {

	private final MeterRegistry registry;

	public CheckMetrics(MeterRegistry registry) {
		this.registry = registry;
	}

	public void record(CheckResult result) {
		String outcome = result.isSuccess() ? "up" : "down";

		registry.counter("pulsewatch.checks", "result", outcome).increment();

		Timer.builder("pulsewatch.check.duration")
				.description("How long the final attempt of a check took")
				.tag("result", outcome)
				.publishPercentileHistogram() // lets Grafana compute p95/p99 across instances
				.register(registry)
				.record(result.getLatencyMs(), TimeUnit.MILLISECONDS);

		if (result.getAttemptCount() > 1) {
			registry.counter("pulsewatch.check.retries").increment(result.getAttemptCount() - 1);
		}
	}
}
