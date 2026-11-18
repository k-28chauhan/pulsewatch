package com.karaninder.pulseWatch.stats;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Inserts rows with exact timestamps, which the entities (that stamp "now" themselves) cannot do. */
class StatsTestData {

	private final JdbcClient jdbc;

	StatsTestData(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	void deleteEverything() {
		jdbc.sql("DELETE FROM users").update(); // ON DELETE CASCADE removes monitors, and through them results, rollups, incidents, alerts
	}

	long user(String email) {
		return jdbc.sql("INSERT INTO users (email, password_hash) VALUES (:email, 'hash') RETURNING id")
				.param("email", email)
				.query(Long.class)
				.single();
	}

	/** A monitor owned by a fresh user of its own. */
	long monitor(String name) {
		return monitorOf(user(name + "@example.com"), name);
	}

	long monitorOf(long userId, String name) {
		return jdbc.sql("INSERT INTO monitors (user_id, name, url) VALUES (:user, :name, 'https://example.com') RETURNING id")
				.param("user", userId)
				.param("name", name)
				.query(Long.class)
				.single();
	}

	void success(long monitorId, String checkedAt, int latencyMs) {
		insert(monitorId, Instant.parse(checkedAt), 200, latencyMs, true, null);
	}

	void success(long monitorId, Instant checkedAt, int latencyMs) {
		insert(monitorId, checkedAt, 200, latencyMs, true, null);
	}

	void failure(long monitorId, String checkedAt, int latencyMs) {
		insert(monitorId, Instant.parse(checkedAt), 500, latencyMs, false, "Expected status 200 but got 500");
	}

	void failure(long monitorId, Instant checkedAt, int latencyMs) {
		insert(monitorId, checkedAt, 500, latencyMs, false, "Expected status 200 but got 500");
	}

	void incident(long monitorId, String startedAt) {
		jdbc.sql("INSERT INTO incidents (monitor_id, started_at) VALUES (:id, :startedAt)")
				.param("id", monitorId)
				.param("startedAt", Instant.parse(startedAt).atOffset(ZoneOffset.UTC))
				.update();
	}

	void disable(long monitorId) {
		jdbc.sql("UPDATE monitors SET enabled = FALSE WHERE id = :id").param("id", monitorId).update();
	}

	void hourly(long monitorId, String hourStart, int total, int failed, long latencySumMs) {
		jdbc.sql("""
						INSERT INTO check_results_hourly (monitor_id, hour_start, total_checks, failed_checks, latency_sum_ms)
						VALUES (:monitorId, :hourStart, :total, :failed, :sum)
						""")
				.param("monitorId", monitorId)
				.param("hourStart", Instant.parse(hourStart).atOffset(ZoneOffset.UTC))
				.param("total", total)
				.param("failed", failed)
				.param("sum", latencySumMs)
				.update();
	}

	private void insert(long monitorId, Instant checkedAt, Integer status, int latencyMs, boolean success, String error) {
		jdbc.sql("""
						INSERT INTO check_results (monitor_id, checked_at, status_code, latency_ms, success, error)
						VALUES (:monitorId, :checkedAt, :status, :latency, :success, :error)
						""")
				.param("monitorId", monitorId)
				.param("checkedAt", checkedAt.atOffset(ZoneOffset.UTC))
				.param("status", status)
				.param("latency", latencyMs)
				.param("success", success)
				.param("error", error)
				.update();
	}

	long count(String table) {
		return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
	}

	java.util.Map<String, Object> hourlyRow(long monitorId, String hourStart) {
		return jdbc.sql("SELECT total_checks, failed_checks, latency_sum_ms, latency_max_ms FROM check_results_hourly WHERE monitor_id = :id AND hour_start = :hour")
				.param("id", monitorId)
				.param("hour", Instant.parse(hourStart).atOffset(ZoneOffset.UTC))
				.query()
				.singleRow();
	}

	static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}
}
