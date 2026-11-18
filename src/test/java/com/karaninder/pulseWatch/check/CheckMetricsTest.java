package com.karaninder.pulseWatch.check;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CheckMetricsTest {

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
	private final CheckMetrics metrics = new CheckMetrics(registry);

	private CheckResult up(int latencyMs, int attempts) {
		return new CheckResult(1L, Instant.now(), CheckOutcome.responded(200, latencyMs, 200), attempts);
	}

	private CheckResult down(int latencyMs, int attempts) {
		return new CheckResult(1L, Instant.now(), CheckOutcome.failed(latencyMs, "boom"), attempts);
	}

	@Test
	void checksAreCountedByResult() {
		metrics.record(up(100, 1));
		metrics.record(up(120, 1));
		metrics.record(down(5000, 3));

		assertThat(registry.get("pulsewatch.checks").tag("result", "up").counter().count()).isEqualTo(2.0);
		assertThat(registry.get("pulsewatch.checks").tag("result", "down").counter().count()).isEqualTo(1.0);
	}

	@Test
	void durationsAreRecordedPerResult() {
		metrics.record(up(100, 1));
		metrics.record(up(300, 1));

		var timer = registry.get("pulsewatch.check.duration").tag("result", "up").timer();
		assertThat(timer.count()).isEqualTo(2);
		assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(400.0);
	}

	@Test
	void onlyExtraAttemptsCountAsRetries() {
		metrics.record(up(100, 1));   // no retry
		metrics.record(down(5000, 3)); // 2 retries

		assertThat(registry.get("pulsewatch.check.retries").counter().count()).isEqualTo(2.0);
	}

	@Test
	void noMeterCarriesAMonitorIdTagBecauseThatWouldMultiplyTheTimeSeries() {
		metrics.record(up(100, 1));
		metrics.record(down(100, 1));

		registry.getMeters().forEach(meter ->
				assertThat(meter.getId().getTags()).extracting(tag -> tag.getKey()).doesNotContain("monitor", "monitorId", "url"));
	}
}
