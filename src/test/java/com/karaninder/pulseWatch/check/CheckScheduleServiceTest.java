package com.karaninder.pulseWatch.check;

import com.karaninder.pulseWatch.monitor.Monitor;
import com.karaninder.pulseWatch.monitor.MonitorMethod;
import com.karaninder.pulseWatch.monitor.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckScheduleServiceTest {

	@Mock
	MonitorRepository monitors;

	private Monitor monitor(long id, int intervalSeconds) {
		Monitor m = new Monitor(1L, "m" + id, "https://example.com/" + id, MonitorMethod.GET, 200, intervalSeconds, 5000, null, 3);
		ReflectionTestUtils.setField(m, "id", id);
		return m;
	}

	@Test
	void claimingMovesNextCheckOneIntervalAheadAndReturnsTargets() {
		Instant now = Instant.parse("2026-11-09T10:00:00Z");
		Monitor every60 = monitor(1, 60);
		Monitor every30 = monitor(2, 30);
		when(monitors.findByEnabledTrueAndNextCheckAtLessThanEqual(now)).thenReturn(List.of(every60, every30));

		List<CheckTarget> targets = new CheckScheduleService(monitors).claimDueMonitors(now);

		assertThat(targets).extracting(CheckTarget::monitorId).containsExactly(1L, 2L);
		assertThat(every60.getNextCheckAt()).isEqualTo(Instant.parse("2026-11-09T10:01:00Z"));
		assertThat(every30.getNextCheckAt()).isEqualTo(Instant.parse("2026-11-09T10:00:30Z"));
	}

	@Test
	void targetCarriesTheIntervalAndFailureThresholdForRetriesAndIncidents() {
		Instant now = Instant.parse("2026-11-09T10:00:00Z");
		when(monitors.findByEnabledTrueAndNextCheckAtLessThanEqual(now)).thenReturn(List.of(monitor(1, 30)));

		CheckTarget target = new CheckScheduleService(monitors).claimDueMonitors(now).get(0);

		assertThat(target.intervalSeconds()).isEqualTo(30);
		assertThat(target.failureThreshold()).isEqualTo(3);
	}

	@Test
	void nothingDueMeansNothingReturned() {
		Instant now = Instant.parse("2026-11-09T10:00:00Z");
		when(monitors.findByEnabledTrueAndNextCheckAtLessThanEqual(now)).thenReturn(List.of());

		assertThat(new CheckScheduleService(monitors).claimDueMonitors(now)).isEmpty();
	}

	@Test
	void aClaimedMonitorIsNoLongerDueOnTheNextTick() {
		Instant now = Instant.parse("2026-11-09T10:00:00Z");
		Monitor m = monitor(1, 60);
		when(monitors.findByEnabledTrueAndNextCheckAtLessThanEqual(now)).thenReturn(List.of(m));

		new CheckScheduleService(monitors).claimDueMonitors(now);

		// A tick 10 s later would ask for next_check_at <= 10:00:10; the claimed monitor is not in that set.
		assertThat(m.getNextCheckAt()).isAfter(now.plusSeconds(10));
	}
}
