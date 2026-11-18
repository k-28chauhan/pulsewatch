package com.karaninder.pulseWatch.stats;

/** The state a monitor is in right now, for the list view. Decided in StatsService.statusOf. */
public enum MonitorStatus {
	/** The last check succeeded. */
	UP,
	/** The last check failed but no incident is open yet (still below the failure threshold). */
	DEGRADED,
	/** An incident is open. */
	DOWN,
	/** Switched off by the user. */
	PAUSED,
	/** Never checked yet. */
	PENDING
}
