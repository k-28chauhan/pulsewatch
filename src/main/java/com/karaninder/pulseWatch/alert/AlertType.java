package com.karaninder.pulseWatch.alert;

/** What an alert is about. Stored as text in alert_events.type (@Enumerated(STRING)). */
public enum AlertType {
	INCIDENT_OPENED,
	INCIDENT_RESOLVED,
	LATENCY_EXCEEDED
}
