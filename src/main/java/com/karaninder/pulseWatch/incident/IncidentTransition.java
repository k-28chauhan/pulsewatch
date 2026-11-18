package com.karaninder.pulseWatch.incident;

/** What a finished check did to the monitor's incident state. Phase 4 will send alerts on OPENED and RESOLVED. */
public enum IncidentTransition {
	/** Nothing changed (still healthy, still failing below the threshold, or an incident that stays open). */
	NONE,
	/** The failure threshold was just reached: a new incident was opened. */
	OPENED,
	/** The first success after an outage: the open incident was resolved. */
	RESOLVED
}
