package com.karaninder.pulseWatch.check;

/**
 * What happened when we called a URL. A check never "throws": every way it can go wrong
 * (500, timeout, DNS failure, refused connection) is just another outcome with success = false.
 *
 * @param statusCode HTTP status, or null if no response arrived at all
 * @param latencyMs  how long we waited (for failures: until we gave up)
 * @param error      human-readable reason, null when successful
 */
public record CheckOutcome(Integer statusCode, int latencyMs, boolean success, String error) {

	/** We got an HTTP response; success only if the status is the one the monitor expects. */
	public static CheckOutcome responded(int statusCode, int latencyMs, int expectedStatus) {
		boolean ok = statusCode == expectedStatus;
		String error = ok ? null : "Expected status " + expectedStatus + " but got " + statusCode;
		return new CheckOutcome(statusCode, latencyMs, ok, error);
	}

	/** No HTTP response (timeout, connection refused, DNS failure...). */
	public static CheckOutcome failed(int latencyMs, String error) {
		return new CheckOutcome(null, latencyMs, false, error);
	}
}
