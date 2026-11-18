package com.karaninder.pulseWatch.incident;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One outage of one monitor. It is "open" while resolvedAt is null.
 * The only way to change it is resolve(), which can happen exactly once.
 */
@Entity
@Table(name = "incidents")
public class Incident {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "monitor_id", nullable = false, updatable = false)
	private Long monitorId;

	@Column(name = "started_at", nullable = false, updatable = false)
	private Instant startedAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(updatable = false)
	private String error;

	protected Incident() {
	}

	private Incident(Long monitorId, Instant startedAt, String error) {
		this.monitorId = monitorId;
		this.startedAt = startedAt;
		this.error = error;
	}

	/** A named factory reads better than a bare constructor: Incident.open(...) says what happens. */
	public static Incident open(Long monitorId, Instant startedAt, String error) {
		return new Incident(monitorId, startedAt, error);
	}

	public void resolve(Instant at) {
		if (resolvedAt != null) {
			throw new IllegalStateException("Incident " + id + " is already resolved");
		}
		this.resolvedAt = at;
	}

	public boolean isOpen() {
		return resolvedAt == null;
	}

	public Long getId() {
		return id;
	}

	public Long getMonitorId() {
		return monitorId;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public String getError() {
		return error;
	}
}
