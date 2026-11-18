package com.karaninder.pulseWatch.monitor;

/**
 * The HTTP methods a monitor may use. An enum (not a String) means a typo like "GTE"
 * is rejected when the JSON is read, long before it reaches the database.
 * Named MonitorMethod to avoid clashing with Spring's own HttpMethod class.
 */
public enum MonitorMethod {
	GET,
	HEAD,
	POST
}
