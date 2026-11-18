package com.karaninder.pulseWatch.stats;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Aggregate SQL for statistics. Plain SQL through JdbcClient, because grouping, percentile_cont and
 * UNION ALL do not map onto JPA entities.
 * <p>
 * The "boundary" is the instant where raw rows end and hourly rollups begin. A query reads
 * hourly rows for hours BEFORE the boundary and raw rows FROM the boundary on, so no check is counted
 * twice, even while old raw rows have not been deleted yet.
 */
@Repository
public class StatsRepository {

	/**
	 * The same hourly shape from both sources: raw rows are grouped into hours on the fly,
	 * rollup rows are used as they are. date_trunc(..., 'UTC') makes hours independent of the session time zone.
	 */
	private static final String HOURLY_UNION = """
			WITH hourly AS (
			    SELECT date_trunc('hour', checked_at, 'UTC')                      AS hour_start,
			           count(*)                                                    AS total_checks,
			           count(*) FILTER (WHERE NOT success)                         AS failed_checks,
			           COALESCE(sum(latency_ms) FILTER (WHERE success), 0)         AS latency_sum_ms
			    FROM check_results
			    WHERE monitor_id = :monitorId AND checked_at >= :rawFrom
			    GROUP BY 1
			    UNION ALL
			    SELECT hour_start, total_checks, failed_checks, latency_sum_ms
			    FROM check_results_hourly
			    WHERE monitor_id = :monitorId AND hour_start >= :from AND hour_start < :boundary
			)
			""";

	private final JdbcClient jdbc;

	public StatsRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Totals for [from, now]. */
	public WindowCounts counts(long monitorId, Instant from, Instant boundary) {
		return jdbc.sql(HOURLY_UNION + """
						SELECT COALESCE(sum(total_checks), 0)   AS total_checks,
						       COALESCE(sum(failed_checks), 0)  AS failed_checks,
						       COALESCE(sum(latency_sum_ms), 0) AS latency_sum_ms
						FROM hourly
						""")
				.param("monitorId", monitorId)
				.param("from", utc(from))
				.param("rawFrom", utc(max(from, boundary)))
				.param("boundary", utc(boundary))
				.query((rs, row) -> new WindowCounts(rs.getLong("total_checks"), rs.getLong("failed_checks"), rs.getLong("latency_sum_ms")))
				.single();
	}

	/** Error-rate buckets of the given size over [from, now], oldest first. Bucket edges are aligned to the Unix epoch (UTC). */
	public List<ErrorRateBucket> errorRateBuckets(long monitorId, Instant from, Instant boundary, long bucketSeconds) {
		return jdbc.sql(HOURLY_UNION + """
						SELECT to_timestamp(floor(extract(epoch FROM hour_start) / :bucketSeconds) * :bucketSeconds) AS bucket_start,
						       sum(total_checks)  AS total_checks,
						       sum(failed_checks) AS failed_checks
						FROM hourly
						GROUP BY 1
						ORDER BY 1
						""")
				.param("monitorId", monitorId)
				.param("from", utc(from))
				.param("rawFrom", utc(max(from, boundary)))
				.param("boundary", utc(boundary))
				.param("bucketSeconds", bucketSeconds)
				.query((rs, row) -> ErrorRateBucket.of(
						rs.getObject("bucket_start", OffsetDateTime.class).toInstant(),
						rs.getLong("total_checks"),
						rs.getLong("failed_checks")))
				.list();
	}

	/**
	 * Latency percentiles of successful checks since {@code from}, from RAW rows only (percentiles cannot be
	 * rebuilt from hourly summaries). percentile_cont interpolates between neighbouring values.
	 */
	public LatencyStats latency(long monitorId, Instant from) {
		return jdbc.sql("""
						SELECT percentile_cont(0.50) WITHIN GROUP (ORDER BY latency_ms) AS p50,
						       percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) AS p95,
						       percentile_cont(0.99) WITHIN GROUP (ORDER BY latency_ms) AS p99,
						       max(latency_ms)                                          AS max_ms
						FROM check_results
						WHERE monitor_id = :monitorId AND checked_at >= :from AND success
						""")
				.param("monitorId", monitorId)
				.param("from", utc(from))
				.query((rs, row) -> new LatencyStats(
						nullableDouble(rs, "p50"), nullableDouble(rs, "p95"), nullableDouble(rs, "p99"),
						(Integer) rs.getObject("max_ms")))
				.single();
	}

	/**
	 * Latency percentiles per time bucket since {@code from}, oldest first, from RAW rows (successful checks only).
	 * Buckets with no successful check are simply absent.
	 */
	public List<LatencyBucket> latencyBuckets(long monitorId, Instant from, long bucketSeconds) {
		return jdbc.sql("""
						SELECT to_timestamp(floor(extract(epoch FROM checked_at) / :bucketSeconds) * :bucketSeconds) AS bucket_start,
						       percentile_cont(0.50) WITHIN GROUP (ORDER BY latency_ms) AS p50,
						       percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) AS p95,
						       count(*)                                                 AS checks
						FROM check_results
						WHERE monitor_id = :monitorId AND checked_at >= :from AND success
						GROUP BY 1
						ORDER BY 1
						""")
				.param("monitorId", monitorId)
				.param("from", utc(from))
				.param("bucketSeconds", bucketSeconds)
				.query((rs, row) -> new LatencyBucket(
						rs.getObject("bucket_start", OffsetDateTime.class).toInstant(),
						nullableDouble(rs, "p50"), nullableDouble(rs, "p95"), rs.getLong("checks")))
				.list();
	}

	/**
	 * One row per monitor of the user: its latest check, its last-24h counts and whether an incident is open,
	 * all in ONE query (LATERAL joins run a small subquery per monitor). Without this the list page would need
	 * one request per monitor.
	 */
	List<OverviewRow> overview(long userId, Instant since) {
		return jdbc.sql("""
						SELECT m.id, m.name, m.url, m.enabled, m.interval_seconds,
						       last.checked_at AS last_checked_at, last.success AS last_success, last.latency_ms AS last_latency_ms,
						       COALESCE(day.total_checks, 0)  AS total_checks,
						       COALESCE(day.failed_checks, 0) AS failed_checks,
						       EXISTS (SELECT 1 FROM incidents i WHERE i.monitor_id = m.id AND i.resolved_at IS NULL) AS has_open_incident
						FROM monitors m
						LEFT JOIN LATERAL (
						    SELECT r.checked_at, r.success, r.latency_ms
						    FROM check_results r
						    WHERE r.monitor_id = m.id
						    ORDER BY r.checked_at DESC
						    LIMIT 1
						) last ON TRUE
						LEFT JOIN LATERAL (
						    SELECT count(*) AS total_checks, count(*) FILTER (WHERE NOT r.success) AS failed_checks
						    FROM check_results r
						    WHERE r.monitor_id = m.id AND r.checked_at >= :since
						) day ON TRUE
						WHERE m.user_id = :userId
						ORDER BY m.id
						""")
				.param("userId", userId)
				.param("since", utc(since))
				.query((rs, row) -> new OverviewRow(
						rs.getLong("id"), rs.getString("name"), rs.getString("url"), rs.getBoolean("enabled"),
						rs.getInt("interval_seconds"),
						rs.getObject("last_checked_at", OffsetDateTime.class) == null ? null
								: rs.getObject("last_checked_at", OffsetDateTime.class).toInstant(),
						(Boolean) rs.getObject("last_success"), (Integer) rs.getObject("last_latency_ms"),
						rs.getLong("total_checks"), rs.getLong("failed_checks"), rs.getBoolean("has_open_incident")))
				.list();
	}

	/**
	 * Summarises every COMPLETE hour still present in check_results (everything before currentHourStart)
	 * into check_results_hourly. Cleanup deletes whole hours only, so an hour in the raw table is never partial.
	 * An upsert (INSERT ... ON CONFLICT DO UPDATE), so running it twice, or after a missed night, is harmless.
	 *
	 * @return number of hourly rows written
	 */
	public int rollUp(Instant currentHourStart) {
		return jdbc.sql("""
						INSERT INTO check_results_hourly (monitor_id, hour_start, total_checks, failed_checks, latency_sum_ms, latency_max_ms)
						SELECT monitor_id,
						       date_trunc('hour', checked_at, 'UTC'),
						       count(*),
						       count(*) FILTER (WHERE NOT success),
						       COALESCE(sum(latency_ms) FILTER (WHERE success), 0),
						       max(latency_ms) FILTER (WHERE success)
						FROM check_results
						WHERE checked_at < :currentHourStart
						GROUP BY monitor_id, 2
						ON CONFLICT (monitor_id, hour_start) DO UPDATE SET
						       total_checks   = EXCLUDED.total_checks,
						       failed_checks  = EXCLUDED.failed_checks,
						       latency_sum_ms = EXCLUDED.latency_sum_ms,
						       latency_max_ms = EXCLUDED.latency_max_ms
						""")
				.param("currentHourStart", utc(currentHourStart))
				.update();
	}

	/** @return number of raw rows deleted */
	public int deleteRawBefore(Instant cutoff) {
		return jdbc.sql("DELETE FROM check_results WHERE checked_at < :cutoff")
				.param("cutoff", utc(cutoff))
				.update();
	}

	// The PostgreSQL driver is happiest with OffsetDateTime, so Instants are converted at the edge.
	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant max(Instant a, Instant b) {
		return a.isAfter(b) ? a : b;
	}

	private static Double nullableDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
		double value = rs.getDouble(column);
		return rs.wasNull() ? null : Math.round(value * 10) / 10.0; // one decimal: 403.09999999999997 becomes 403.1
	}
}
