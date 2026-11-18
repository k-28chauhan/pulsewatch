package com.karaninder.pulseWatch.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One row of the alert_events audit log. Immutable: history is never edited. */
@Entity
@Table(name = "alert_events")
public class AlertEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "monitor_id", nullable = false, updatable = false)
	private Long monitorId;

	@Column(name = "incident_id", updatable = false)
	private Long incidentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private AlertType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private AlertChannel channel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private AlertStatus status;

	@Column(updatable = false)
	private String error;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AlertEvent() {
	}

	public AlertEvent(AlertMessage message, AlertChannel channel, AlertStatus status, String error, Instant createdAt) {
		this.monitorId = message.monitorId();
		this.incidentId = message.incidentId();
		this.type = message.type();
		this.channel = channel;
		this.status = status;
		this.error = error;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public Long getMonitorId() {
		return monitorId;
	}

	public Long getIncidentId() {
		return incidentId;
	}

	public AlertType getType() {
		return type;
	}

	public AlertChannel getChannel() {
		return channel;
	}

	public AlertStatus getStatus() {
		return status;
	}

	public String getError() {
		return error;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
