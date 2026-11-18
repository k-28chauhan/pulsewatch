package com.karaninder.pulseWatch.stats;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsMaintenanceServiceTest {

	@Mock
	StatsRepository stats;

	@Test
	void summarisesCompleteHoursFirstAndOnlyThenDeletesRowsOlderThanTheHourAlignedRetention() {
		Instant now = Instant.parse("2026-11-10T03:00:07Z");
		when(stats.rollUp(Instant.parse("2026-11-10T03:00:00Z"))).thenReturn(48);
		when(stats.deleteRawBefore(Instant.parse("2026-11-03T03:00:00Z"))).thenReturn(1200);

		StatsMaintenanceService.Result result =
				new StatsMaintenanceService(stats, new StatsProperties(Duration.ofDays(7))).run(now);

		InOrder order = inOrder(stats);
		order.verify(stats).rollUp(Instant.parse("2026-11-10T03:00:00Z")); // the running hour is excluded
		order.verify(stats).deleteRawBefore(Instant.parse("2026-11-03T03:00:00Z")); // never before the rollup
		assertThat(result).isEqualTo(new StatsMaintenanceService.Result(48, 1200));
	}

	@Test
	void theCutoffIsAlignedToAWholeHourSoNoHourIsEverDeletedHalf() {
		Instant now = Instant.parse("2026-11-10T03:45:30Z");

		new StatsMaintenanceService(stats, new StatsProperties(Duration.ofDays(7))).run(now);

		org.mockito.Mockito.verify(stats).deleteRawBefore(Instant.parse("2026-11-03T03:00:00Z")); // not 03:45:30
	}
}
