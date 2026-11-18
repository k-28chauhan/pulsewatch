package com.karaninder.pulseWatch.monitor;

/** The monitor's URL points somewhere only the server's own network can reach. Answered with HTTP 400. */
public class ForbiddenTargetException extends RuntimeException {

	public ForbiddenTargetException(String reason) {
		super(reason);
	}
}
