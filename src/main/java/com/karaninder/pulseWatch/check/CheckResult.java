package com.karaninder.pulseWatch.check;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row of check_results. Immutable by design: a historical record is never edited.
 * <p>
 * It stores monitorId as a plain number instead of a @ManyToOne Monitor reference. The check
 * path only ever needs the id, and avoiding the relationship avoids loading monitors by accident.
 */
@Entity
@Table(name = "check_results")
public class CheckResult {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "monitor_id", nullable = false, updatable = false)
	private Long monitorId;

	@Column(name = "checked_at", nullable = false, updatable = false)
	private Instant checkedAt;

	@Column(name = "status_code", updatable = false)
	private Integer statusCode;

	@Column(name = "latency_ms", nullable = false, updatable = false)
	private int latencyMs;

	@Column(nullable = false, updatable = false)
	private boolean success;

	@Column(updatable = false)
	private String error;

	@Column(name = "attempt_count", nullable = false, updatable = false)
	private int attemptCount;

	protected CheckResult() {
	}

	public CheckResult(Long monitorId, Instant checkedAt, CheckOutcome outcome, int attemptCount) {
		this.monitorId = monitorId;
		this.checkedAt = checkedAt;
		this.statusCode = outcome.statusCode();
		this.latencyMs = outcome.latencyMs();
		this.success = outcome.success();
		this.error = outcome.error();
		this.attemptCount = attemptCount;
	}

	public Long getId() {
		return id;
	}

	public Long getMonitorId() {
		return monitorId;
	}

	public Instant getCheckedAt() {
		return checkedAt;
	}

	public Integer getStatusCode() {
		return statusCode;
	}

	public int getLatencyMs() {
		return latencyMs;
	}

	public boolean isSuccess() {
		return success;
	}

	public String getError() {
		return error;
	}

	public int getAttemptCount() {
		return attemptCount;
	}
}
