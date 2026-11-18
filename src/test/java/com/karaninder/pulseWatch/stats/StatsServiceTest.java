package com.karaninder.pulseWatch.stats;

import com.karaninder.pulseWatch.incident.Incident;
import com.karaninder.pulseWatch.incident.IncidentRepository;
import com.karaninder.pulseWatch.monitor.MonitorNotFoundException;
import com.karaninder.pulseWatch.monitor.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

	private static final long USER = 7L;
	private static final Instant NOW = Instant.parse("2026-11-10T12:30:00Z");
	private static final Instant BOUNDARY = Instant.parse("2026-11-03T12:00:00Z"); // hour start of NOW minus 7 days

	@Mock
	MonitorRepository monitors;

	@Mock
	IncidentRepository incidents;

	@Mock
	StatsRepository stats;

	private StatsService service() {
		return new StatsService(monitors, incidents, stats, new StatsProperties(Duration.ofDays(7)),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	// ---- the arithmetic ----

	@Test
	void uptimeIsTheShareOfSuccessfulChecksRoundedToThreeDecimals() {
		UptimeStats uptime = StatsService.toUptime(new WindowCounts(1000, 3, 99_700));

		assertThat(uptime.uptimePercent()).isEqualTo(99.7);
		assertThat(uptime.totalChecks()).isEqualTo(1000);
		assertThat(uptime.failedChecks()).isEqualTo(3);
		assertThat(uptime.avgLatencyMs()).isEqualTo(100.0); // 99 700 ms over 997 successful checks
	}

	@Test
	void anAwkwardFractionIsRoundedNotTruncated() {
		// 2 of 3 successful = 66.6666...%
		assertThat(StatsService.toUptime(new WindowCounts(3, 1, 300)).uptimePercent()).isEqualTo(66.667);
	}

	@Test
	void noChecksMeansNoDataNotHundredPercent() {
		UptimeStats uptime = StatsService.toUptime(new WindowCounts(0, 0, 0));

		assertThat(uptime.uptimePercent()).isNull();
		assertThat(uptime.avgLatencyMs()).isNull();
	}

	@Test
	void onlyFailuresMeansZeroUptimeAndNoAverageLatency() {
		UptimeStats uptime = StatsService.toUptime(new WindowCounts(5, 5, 0));

		assertThat(uptime.uptimePercent()).isEqualTo(0.0);
		assertThat(uptime.avgLatencyMs()).isNull();
	}

	// ---- composing the response ----

	@Test
	void theBoundaryIsTheStartOfTheHourSevenDaysAgoAndEachWindowStartsAtNowMinusItsLength() {
		when(monitors.existsByIdAndUserId(5L, USER)).thenReturn(true);
		when(stats.counts(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(BOUNDARY)))
				.thenReturn(new WindowCounts(10, 1, 900));
		when(stats.latency(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.any()))
				.thenReturn(new LatencyStats(100.0, 200.0, 300.0, 400));
		when(stats.errorRateBuckets(5L, NOW.minus(Duration.ofHours(24)), BOUNDARY, 3600)).thenReturn(List.of());
		when(incidents.findByMonitorIdAndResolvedAtIsNull(5L)).thenReturn(Optional.empty());

		StatsResponse response = service().stats(USER, 5L);

		verify(stats).counts(5L, NOW.minus(Duration.ofHours(24)), BOUNDARY);
		verify(stats).counts(5L, NOW.minus(Duration.ofDays(7)), BOUNDARY);
		verify(stats).counts(5L, NOW.minus(Duration.ofDays(30)), BOUNDARY);
		verify(stats).latency(5L, NOW.minus(Duration.ofHours(24)));
		verify(stats).latency(5L, NOW.minus(Duration.ofDays(7)));
		assertThat(response.generatedAt()).isEqualTo(NOW);
		assertThat(response.uptime24h().uptimePercent()).isEqualTo(90.0);
		assertThat(response.latency7d().p95()).isEqualTo(200.0);
		assertThat(response.openIncident()).isNull();
	}

	@Test
	void anOpenIncidentIsIncludedWithItsDurationSoFar() {
		when(monitors.existsByIdAndUserId(5L, USER)).thenReturn(true);
		when(stats.counts(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(BOUNDARY)))
				.thenReturn(new WindowCounts(0, 0, 0));
		when(stats.latency(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.any()))
				.thenReturn(new LatencyStats(null, null, null, null));
		when(stats.errorRateBuckets(org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(BOUNDARY), org.mockito.ArgumentMatchers.anyLong()))
				.thenReturn(List.of());
		when(incidents.findByMonitorIdAndResolvedAtIsNull(5L))
				.thenReturn(Optional.of(Incident.open(5L, NOW.minusSeconds(600), "boom")));

		StatsResponse response = service().stats(USER, 5L);

		assertThat(response.openIncident().open()).isTrue();
		assertThat(response.openIncident().durationSeconds()).isEqualTo(600);
		assertThat(response.openIncident().error()).isEqualTo("boom");
	}

	@Test
	void errorRateUsesTheBucketSizeOfTheChosenWindow() {
		when(monitors.existsByIdAndUserId(5L, USER)).thenReturn(true);
		when(stats.errorRateBuckets(5L, NOW.minus(Duration.ofDays(30)), BOUNDARY, 86_400)).thenReturn(List.of());

		assertThat(service().errorRate(USER, 5L, StatsWindow.LAST_30D)).isEmpty();

		verify(stats).errorRateBuckets(5L, NOW.minus(Duration.ofDays(30)), BOUNDARY, 86_400);
	}

	@Test
	void anUnknownMonitorIsNotFoundAndNoStatsAreQueried() {
		when(monitors.existsByIdAndUserId(99L, USER)).thenReturn(false);

		assertThatThrownBy(() -> service().stats(USER, 99L)).isInstanceOf(MonitorNotFoundException.class);
		assertThatThrownBy(() -> service().errorRate(USER, 99L, StatsWindow.LAST_24H)).isInstanceOf(MonitorNotFoundException.class);
		verifyNoInteractions(stats);
	}

	// ---- the list view ----

	private OverviewRow row(boolean enabled, Boolean lastSuccess, Instant lastCheckedAt, boolean openIncident) {
		return new OverviewRow(1L, "Homepage", "https://example.com", enabled, 60, lastCheckedAt, lastSuccess,
				lastSuccess == null ? null : 120, 10, 1, openIncident);
	}

	@Test
	void statusFollowsTheRulesInOrderPausedThenDownThenPendingThenDegradedThenUp() {
		Instant t = NOW.minusSeconds(30);

		assertThat(StatsService.statusOf(row(false, true, t, true))).isEqualTo(MonitorStatus.PAUSED);    // paused wins over everything
		assertThat(StatsService.statusOf(row(true, false, t, true))).isEqualTo(MonitorStatus.DOWN);      // an open incident
		assertThat(StatsService.statusOf(row(true, null, null, false))).isEqualTo(MonitorStatus.PENDING); // never checked
		assertThat(StatsService.statusOf(row(true, false, t, false))).isEqualTo(MonitorStatus.DEGRADED); // failing, no incident yet
		assertThat(StatsService.statusOf(row(true, true, t, false))).isEqualTo(MonitorStatus.UP);
	}

	@Test
	void overviewAddsUptimeAndHidesTheLatencyOfAFailedCheck() {
		when(stats.overview(USER, NOW.minus(Duration.ofHours(24)))).thenReturn(List.of(
				row(true, true, NOW.minusSeconds(30), false),     // 10 checks, 1 failed: 90 %
				row(true, false, NOW.minusSeconds(30), false)));  // last check failed: no latency shown

		List<MonitorOverview> overview = service().overview(USER);

		assertThat(overview).hasSize(2);
		assertThat(overview.get(0).uptime24h()).isEqualTo(90.0);
		assertThat(overview.get(0).lastLatencyMs()).isEqualTo(120);
		assertThat(overview.get(0).status()).isEqualTo(MonitorStatus.UP);
		assertThat(overview.get(1).lastLatencyMs()).isNull();
		assertThat(overview.get(1).status()).isEqualTo(MonitorStatus.DEGRADED);
	}

	@Test
	void aMonitorWithNoChecksInTheLastDayHasNoUptimeFigure() {
		when(stats.overview(USER, NOW.minus(Duration.ofHours(24)))).thenReturn(List.of(
				new OverviewRow(1L, "x", "https://x", true, 60, null, null, null, 0, 0, false)));

		assertThat(service().overview(USER).get(0).uptime24h()).isNull();
	}

	// ---- latency series ----

	@Test
	void latencySeriesUsesTheWindowsBucketSize() {
		when(monitors.existsByIdAndUserId(5L, USER)).thenReturn(true);
		when(stats.latencyBuckets(5L, NOW.minus(Duration.ofHours(24)), 900)).thenReturn(List.of());

		assertThat(service().latencySeries(USER, 5L, StatsWindow.LAST_24H)).isEmpty();

		verify(stats).latencyBuckets(5L, NOW.minus(Duration.ofHours(24)), 900);
	}

	@Test
	void thirtyDayLatencySeriesIsRefusedBecauseRawRowsAreOnlyKeptSevenDays() {
		when(monitors.existsByIdAndUserId(5L, USER)).thenReturn(true);

		assertThatThrownBy(() -> service().latencySeries(USER, 5L, StatsWindow.LAST_30D))
				.isInstanceOf(InvalidStatsWindowException.class)
				.hasMessageContaining("24h and 7d only");
	}

	@Test
	void latencySeriesOfSomeoneElsesMonitorIsNotFound() {
		when(monitors.existsByIdAndUserId(5L, USER)).thenReturn(false);

		assertThatThrownBy(() -> service().latencySeries(USER, 5L, StatsWindow.LAST_24H))
				.isInstanceOf(MonitorNotFoundException.class);
	}
}
