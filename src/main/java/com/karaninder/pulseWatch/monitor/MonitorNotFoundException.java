package com.karaninder.pulseWatch.monitor;

/**
 * Unchecked (extends RuntimeException) so callers aren't forced to catch it;
 * the global error handler turns it into an HTTP 404.
 */
public class MonitorNotFoundException extends RuntimeException {

	public MonitorNotFoundException(Long id) {
		super("Monitor " + id + " not found");
	}
}
