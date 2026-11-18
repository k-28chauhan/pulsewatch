package com.karaninder.pulseWatch.incident;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentMetricsTest {

	@Mock
	IncidentRepository incidents;

	@Test
	void theGaugeReadsTheCurrentCountEveryTimeItIsScraped() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		new IncidentMetrics(registry, incidents);

		when(incidents.countByResolvedAtIsNull()).thenReturn(2L);
		assertThat(registry.get("pulsewatch.incidents.open").gauge().value()).isEqualTo(2.0);

		when(incidents.countByResolvedAtIsNull()).thenReturn(0L); // an incident resolved: the same gauge now says 0
		assertThat(registry.get("pulsewatch.incidents.open").gauge().value()).isEqualTo(0.0);
	}
}
