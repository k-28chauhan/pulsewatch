package com.karaninder.pulseWatch.monitor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity: one row of the "monitors" table.
 * <p>
 * There are no public setters on purpose. The only ways to change a Monitor are the
 * methods below (applyChanges, pause, resume), so the rules live in one place.
 */
@Entity
@Table(name = "monitors")
public class Monitor {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY) // the database assigns the id
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId; // the owner; a monitor never changes hands

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String url;

	@Enumerated(EnumType.STRING) // store "GET", not 0 - survives reordering the enum
	@Column(nullable = false)
	private MonitorMethod method;

	@Column(name = "expected_status", nullable = false)
	private int expectedStatus;

	@Column(name = "interval_seconds", nullable = false)
	private int intervalSeconds;

	@Column(name = "timeout_ms", nullable = false)
	private int timeoutMs;

	@Column(name = "latency_threshold_ms")
	private Integer latencyThresholdMs; // Integer (not int) because null means "no threshold"

	@Column(name = "failure_threshold", nullable = false)
	private int failureThreshold;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "next_check_at", nullable = false)
	private Instant nextCheckAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** JPA needs a no-argument constructor to build objects from query results. */
	protected Monitor() {
	}

	public Monitor(Long userId, String name, String url, MonitorMethod method, int expectedStatus,
	               int intervalSeconds, int timeoutMs, Integer latencyThresholdMs, int failureThreshold) {
		this.userId = userId;
		applyChanges(name, url, method, expectedStatus, intervalSeconds, timeoutMs, latencyThresholdMs, failureThreshold);
		this.enabled = true;
		this.nextCheckAt = Instant.now(); // due immediately, so the first check happens right away
	}

	/** Replaces every user-editable field (used by both the constructor and PUT). */
	public void applyChanges(String name, String url, MonitorMethod method, int expectedStatus,
	                         int intervalSeconds, int timeoutMs, Integer latencyThresholdMs, int failureThreshold) {
		this.name = name;
		this.url = url;
		this.method = method;
		this.expectedStatus = expectedStatus;
		this.intervalSeconds = intervalSeconds;
		this.timeoutMs = timeoutMs;
		this.latencyThresholdMs = latencyThresholdMs;
		this.failureThreshold = failureThreshold;
	}

	public void pause() {
		this.enabled = false;
	}

	public void resume() {
		this.enabled = true;
		this.nextCheckAt = Instant.now(); // don't wait out the old schedule after a long pause
	}

	/**
	 * Called when the scheduler "claims" this monitor for a check: pushes the due time one
	 * interval into the future so the next scheduler tick does not pick it up again.
	 * Measured from now (not from the old nextCheckAt) so that after downtime we run one
	 * catch-up check instead of a burst of missed ones.
	 */
	public void scheduleNextCheck(Instant now) {
		this.nextCheckAt = now.plusSeconds(intervalSeconds);
	}

	// Lifecycle callbacks: JPA calls these automatically just before INSERT / UPDATE.
	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getName() {
		return name;
	}

	public String getUrl() {
		return url;
	}

	public MonitorMethod getMethod() {
		return method;
	}

	public int getExpectedStatus() {
		return expectedStatus;
	}

	public int getIntervalSeconds() {
		return intervalSeconds;
	}

	public int getTimeoutMs() {
		return timeoutMs;
	}

	public Integer getLatencyThresholdMs() {
		return latencyThresholdMs;
	}

	public int getFailureThreshold() {
		return failureThreshold;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Instant getNextCheckAt() {
		return nextCheckAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
