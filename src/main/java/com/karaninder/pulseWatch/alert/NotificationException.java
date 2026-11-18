package com.karaninder.pulseWatch.alert;

/**
 * A delivery failed (SMTP down, webhook returned 500...). Unchecked, so notifier implementations do not
 * force callers to declare it; AlertService catches it and records a FAILED audit row.
 */
public class NotificationException extends RuntimeException {

	public NotificationException(String message, Throwable cause) {
		super(message, cause);
	}

	public NotificationException(String message) {
		super(message);
	}
}
