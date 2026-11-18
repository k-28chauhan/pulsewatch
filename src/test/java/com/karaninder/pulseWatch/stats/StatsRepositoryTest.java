package com.karaninder.pulseWatch.stats;

import com.karaninder.pulseWatch.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The SQL of the stats feature, against a REAL PostgreSQL 17 (Testcontainers). Mocks cannot test SQL:
 * only a real database proves that percentile_cont, UNION ALL and ON CONFLICT do what we think.
 * <p>
 * The connection pool is told to use the Asia/Kolkata (UTC+5:30) time zone on purpose: if any query
 * depended on the session time zone, hour boundaries would land on :30 and the tests would fail.
 */
@SpringBootTest(properties = {
		"pulsewatch.check.enabled=false",
		"spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'Asia/Kolkata'"
})
@Import(TestcontainersConfig.class)
class StatsRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-11-10T12:30:00Z");
	private static final Instant BOUNDARY = Instant.parse("2026-11-03T12:00:00Z"); // hour start of NOW minus 7 days
	private static final Instant LAST_24H = NOW.minusSeconds(24 * 3600);
	private static final Instant LAST_7D = NOW.minusSeconds(7 * 24 * 3600);
	private static final Instant LAST_30D = NOW.minusSeconds(30 * 24 * 3600);

	@Autowired
	StatsRepository repository;

	@Autowired
	JdbcClient jdbc;

	private StatsTestData data;

	@BeforeEach
	void cleanDatabase() {
		data = new StatsTestData(jdbc);
		data.deleteEverything();
	}

	// ---- counts ----

	@Test
	void countsTotalsFailuresAndSuccessfulLatencyInsideTheWindow() {
		long monitor = data.monitor("a");
		data.success(monitor, "2026-11-10T10:00:00Z", 100);
		data.success(monitor, "2026-11-10T10:30:00Z", 200);
		data.success(monitor, "2026-11-10T11:00:00Z", 300);
		data.failure(monitor, "2026-11-10T11:30:00Z", 5000); // a failure's latency must not count
		data.success(monitor, "2026-11-08T12:00:00Z", 999);  // 2 days ago: outside 24h, inside 7d

		assertThat(repository.counts(monitor, LAST_24H, BOUNDARY))
				.isEqualTo(new WindowCounts(4, 1, 600));
		assertThat(repository.counts(monitor, LAST_7D, BOUNDARY))
				.isEqualTo(new WindowCounts(5, 1, 1599));
	}

	@Test
	void anEmptyWindowCountsZeroInsteadOfReturningNulls() {
		long monitor = data.monitor("empty");

		assertThat(repository.counts(monitor, LAST_24H, BOUNDARY)).isEqualTo(new WindowCounts(0, 0, 0));
	}

	@Test
	void otherMonitorsDataIsNeverMixedIn() {
		long mine = data.monitor("mine");
		long other = data.monitor("other");
		data.success(mine, "2026-11-10T10:00:00Z", 100);
		data.failure(other, "2026-11-10T10:00:00Z", 100);

		assertThat(repository.counts(mine, LAST_24H, BOUNDARY)).isEqualTo(new WindowCounts(1, 0, 100));
	}

	@Test
	void thirtyDayCountsUseRollupsBeforeTheBoundaryAndRawRowsAfterItWithoutDoubleCounting() {
		long monitor = data.monitor("long");
		// Hourly summaries older than the boundary: counted (when inside the window).
		data.hourly(monitor, "2026-10-20T10:00:00Z", 100, 5, 9500);
		data.hourly(monitor, "2026-11-01T10:00:00Z", 4, 0, 400);
		data.hourly(monitor, "2026-10-05T10:00:00Z", 50, 50, 0);   // older than 30 days: outside the window
		// A hourly row AFTER the boundary is ignored: those hours are read from the raw rows instead.
		data.hourly(monitor, "2026-11-09T10:00:00Z", 999, 999, 0);
		// A leftover raw row from before the boundary is ignored too: its hour is represented by the rollup.
		data.success(monitor, "2026-11-01T10:15:00Z", 100);
		// Raw rows after the boundary: counted.
		data.success(monitor, "2026-11-10T11:00:00Z", 200);
		data.failure(monitor, "2026-11-10T11:30:00Z", 5000);

		assertThat(repository.counts(monitor, LAST_30D, BOUNDARY))
				.isEqualTo(new WindowCounts(106, 6, 10100));
	}

	// ---- latency percentiles ----

	@Test
	void percentilesInterpolateBetweenValuesAndIgnoreFailures() {
		long monitor = data.monitor("lat");
		data.success(monitor, "2026-11-10T08:00:00Z", 100);
		data.success(monitor, "2026-11-10T09:00:00Z", 200);
		data.success(monitor, "2026-11-10T10:00:00Z", 300);
		data.success(monitor, "2026-11-10T11:00:00Z", 400);
		data.success(monitor, "2026-11-10T12:00:00Z", 500);
		data.failure(monitor, "2026-11-10T12:10:00Z", 9999);   // excluded: failed
		data.success(monitor, "2026-11-08T12:00:00Z", 1);      // excluded: older than 24h

		LatencyStats latency = repository.latency(monitor, LAST_24H);

		assertThat(latency.p50()).isCloseTo(300.0, within(0.001));
		assertThat(latency.p95()).isCloseTo(480.0, within(0.001)); // position 0.95 * (5 - 1) = 3.8: between 400 and 500
		assertThat(latency.p99()).isCloseTo(496.0, within(0.001));
		assertThat(latency.max()).isEqualTo(500);
	}

	@Test
	void noSuccessfulChecksMeansNullPercentiles() {
		long monitor = data.monitor("allfail");
		data.failure(monitor, "2026-11-10T10:00:00Z", 5000);

		assertThat(repository.latency(monitor, LAST_24H)).isEqualTo(new LatencyStats(null, null, null, null));
	}

	// ---- error-rate buckets ----

	@Test
	void hourlyBucketsCountFailuresPerHourOldestFirst() {
		long monitor = data.monitor("buckets");
		data.success(monitor, "2026-11-10T10:05:00Z", 100);
		data.failure(monitor, "2026-11-10T10:50:00Z", 100);
		data.success(monitor, "2026-11-10T11:10:00Z", 100);

		List<ErrorRateBucket> buckets = repository.errorRateBuckets(monitor, LAST_24H, BOUNDARY, 3600);

		assertThat(buckets).containsExactly(
				ErrorRateBucket.of(Instant.parse("2026-11-10T10:00:00Z"), 2, 1),
				ErrorRateBucket.of(Instant.parse("2026-11-10T11:00:00Z"), 1, 0));
		assertThat(buckets.get(0).errorRate()).isEqualTo(0.5);
	}

	@Test
	void largerBucketsMergeSeveralHours() {
		long monitor = data.monitor("six-hour");
		data.success(monitor, "2026-11-10T10:05:00Z", 100);
		data.failure(monitor, "2026-11-10T10:50:00Z", 100);
		data.success(monitor, "2026-11-10T11:10:00Z", 100);

		assertThat(repository.errorRateBuckets(monitor, LAST_24H, BOUNDARY, 6 * 3600))
				.containsExactly(ErrorRateBucket.of(Instant.parse("2026-11-10T06:00:00Z"), 3, 1));
	}

	@Test
	void bucketsOfAMonitorWithoutDataAreEmpty() {
		assertThat(repository.errorRateBuckets(data.monitor("none"), LAST_24H, BOUNDARY, 3600)).isEmpty();
	}

	// ---- nightly rollup ----

	@Test
	void rollUpSummarisesEveryCompleteHourAndSkipsTheRunningOne() {
		long monitor = data.monitor("rollup");
		data.success(monitor, "2026-11-10T09:10:00Z", 100);
		data.failure(monitor, "2026-11-10T09:40:00Z", 5000);
		data.success(monitor, "2026-11-10T10:20:00Z", 300);
		data.success(monitor, "2026-11-10T12:10:00Z", 50); // the running hour (12:00 to 13:00): incomplete

		int written = repository.rollUp(Instant.parse("2026-11-10T12:00:00Z"));

		assertThat(written).isEqualTo(2);
		var nine = data.hourlyRow(monitor, "2026-11-10T09:00:00Z");
		assertThat(((Number) nine.get("total_checks")).longValue()).isEqualTo(2);
		assertThat(((Number) nine.get("failed_checks")).longValue()).isEqualTo(1);
		assertThat(((Number) nine.get("latency_sum_ms")).longValue()).isEqualTo(100); // failure latency excluded
		assertThat(((Number) nine.get("latency_max_ms")).intValue()).isEqualTo(100);
		var ten = data.hourlyRow(monitor, "2026-11-10T10:00:00Z");
		assertThat(((Number) ten.get("total_checks")).longValue()).isEqualTo(1);
		assertThat(data.count("check_results_hourly")).isEqualTo(2);
	}

	@Test
	void rollUpIsIdempotentAndUpdatesAnHourWhoseRawRowsChanged() {
		long monitor = data.monitor("again");
		data.success(monitor, "2026-11-10T09:10:00Z", 100);
		Instant currentHour = Instant.parse("2026-11-10T12:00:00Z");

		repository.rollUp(currentHour);
		repository.rollUp(currentHour); // second run: no duplicate rows, no error
		assertThat(data.count("check_results_hourly")).isEqualTo(1);

		data.success(monitor, "2026-11-10T09:50:00Z", 200); // a late row for the same hour
		repository.rollUp(currentHour);

		var nine = data.hourlyRow(monitor, "2026-11-10T09:00:00Z");
		assertThat(((Number) nine.get("total_checks")).longValue()).isEqualTo(2); // updated, not duplicated
		assertThat(((Number) nine.get("latency_sum_ms")).longValue()).isEqualTo(300);
		assertThat(data.count("check_results_hourly")).isEqualTo(1);
	}

	@Test
	void anHourWithOnlyFailuresHasNoMaxLatency() {
		long monitor = data.monitor("down-hour");
		data.failure(monitor, "2026-11-10T10:05:00Z", 5000);

		repository.rollUp(Instant.parse("2026-11-10T12:00:00Z"));

		var row = data.hourlyRow(monitor, "2026-11-10T10:00:00Z");
		assertThat(row.get("latency_max_ms")).isNull();
		assertThat(((Number) row.get("latency_sum_ms")).longValue()).isZero();
	}

	@Test
	void deleteRawBeforeRemovesOnlyOlderRows() {
		long monitor = data.monitor("cleanup");
		data.success(monitor, "2026-11-03T11:59:59Z", 100); // just before the cutoff: deleted
		data.success(monitor, "2026-11-03T12:00:00Z", 100); // exactly at the cutoff: kept
		data.success(monitor, "2026-11-05T12:00:00Z", 100);

		int deleted = repository.deleteRawBefore(BOUNDARY);

		assertThat(deleted).isEqualTo(1);
		assertThat(data.count("check_results")).isEqualTo(2);
	}

	// ---- latency series ----

	@Test
	void latencyBucketsGivePercentilesPerBucketForSuccessfulChecksOnly() {
		long monitor = data.monitor("series");
		data.success(monitor, "2026-11-10T10:05:00Z", 100);
		data.success(monitor, "2026-11-10T10:10:00Z", 300);
		data.failure(monitor, "2026-11-10T10:12:00Z", 9999); // excluded
		data.success(monitor, "2026-11-10T10:50:00Z", 200);

		List<LatencyBucket> series = repository.latencyBuckets(monitor, LAST_24H, 900); // 15-minute buckets

		assertThat(series).hasSize(2);
		assertThat(series.get(0).bucketStart()).isEqualTo(Instant.parse("2026-11-10T10:00:00Z"));
		assertThat(series.get(0).p50()).isCloseTo(200.0, within(0.001));
		assertThat(series.get(0).p95()).isCloseTo(290.0, within(0.001)); // 100 + 0.95 * (300 - 100)
		assertThat(series.get(0).checks()).isEqualTo(2);
		assertThat(series.get(1).bucketStart()).isEqualTo(Instant.parse("2026-11-10T10:45:00Z"));
		assertThat(series.get(1).p50()).isCloseTo(200.0, within(0.001));
	}

	@Test
	void anIntervalWithOnlyFailuresProducesNoLatencyPoint() {
		long monitor = data.monitor("series-fail");
		data.failure(monitor, "2026-11-10T10:05:00Z", 5000);

		assertThat(repository.latencyBuckets(monitor, LAST_24H, 900)).isEmpty();
	}

	// ---- monitor overview ----

	@Test
	void overviewDescribesEveryMonitorOfTheUserInOneQuery() {
		long owner = data.user("overview-owner@example.com");
		long stranger = data.user("stranger@example.com");

		long up = data.monitorOf(owner, "up");
		data.success(up, "2026-11-10T10:00:00Z", 100);
		data.success(up, "2026-11-10T11:00:00Z", 200);

		long paused = data.monitorOf(owner, "paused");
		data.disable(paused);

		long fresh = data.monitorOf(owner, "fresh"); // never checked

		long failing = data.monitorOf(owner, "failing");
		data.success(failing, "2026-11-10T10:00:00Z", 100);
		data.failure(failing, "2026-11-10T11:00:00Z", 5000);

		long down = data.monitorOf(owner, "down");
		data.failure(down, "2026-11-10T11:00:00Z", 5000);
		data.incident(down, "2026-11-10T11:00:00Z");

		data.monitorOf(stranger, "not-mine");

		List<OverviewRow> rows = repository.overview(owner, LAST_24H);

		assertThat(rows).extracting(OverviewRow::name).containsExactly("up", "paused", "fresh", "failing", "down"); // ordered by id, stranger's excluded
		OverviewRow upRow = rows.get(0);
		assertThat(upRow.lastCheckedAt()).isEqualTo(Instant.parse("2026-11-10T11:00:00Z"));
		assertThat(upRow.lastSuccess()).isTrue();
		assertThat(upRow.lastLatencyMs()).isEqualTo(200);
		assertThat(upRow.totalChecks()).isEqualTo(2);
		assertThat(upRow.failedChecks()).isZero();
		assertThat(upRow.hasOpenIncident()).isFalse();
		assertThat(upRow.intervalSeconds()).isEqualTo(60);
		OverviewRow freshRow = rows.get(2);
		assertThat(freshRow.lastCheckedAt()).isNull();
		assertThat(freshRow.lastSuccess()).isNull();
		assertThat(freshRow.totalChecks()).isZero();
		assertThat(rows.get(1).enabled()).isFalse();
		OverviewRow failingRow = rows.get(3);
		assertThat(failingRow.lastSuccess()).isFalse();
		assertThat(failingRow.failedChecks()).isEqualTo(1);
		assertThat(rows.get(4).hasOpenIncident()).isTrue();
	}

	@Test
	void overviewCountsOnlyTheLast24HoursButShowsTheLatestCheckEverRecorded() {
		long owner = data.user("old-checks@example.com");
		long monitor = data.monitorOf(owner, "old");
		data.success(monitor, "2026-11-08T10:00:00Z", 100); // 2 days ago: outside the 24 h count

		OverviewRow row = repository.overview(owner, LAST_24H).get(0);

		assertThat(row.totalChecks()).isZero();
		assertThat(row.lastCheckedAt()).isEqualTo(Instant.parse("2026-11-08T10:00:00Z"));
	}
}
