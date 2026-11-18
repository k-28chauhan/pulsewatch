package com.karaninder.pulseWatch.incident;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentTest {

	private final Instant start = Instant.parse("2026-11-09T10:00:00Z");

	@Test
	void aNewIncidentIsOpen() {
		Incident incident = Incident.open(1L, start, "boom");

		assertThat(incident.isOpen()).isTrue();
		assertThat(incident.getResolvedAt()).isNull();
		assertThat(incident.getStartedAt()).isEqualTo(start);
		assertThat(incident.getError()).isEqualTo("boom");
	}

	@Test
	void resolvingClosesIt() {
		Incident incident = Incident.open(1L, start, "boom");

		incident.resolve(start.plusSeconds(120));

		assertThat(incident.isOpen()).isFalse();
		assertThat(incident.getResolvedAt()).isEqualTo(start.plusSeconds(120));
	}

	@Test
	void anIncidentCanBeResolvedOnlyOnce() {
		Incident incident = Incident.open(1L, start, "boom");
		incident.resolve(start.plusSeconds(60));

		assertThatThrownBy(() -> incident.resolve(start.plusSeconds(90))).isInstanceOf(IllegalStateException.class);
		assertThat(incident.getResolvedAt()).isEqualTo(start.plusSeconds(60)); // unchanged
	}
}
