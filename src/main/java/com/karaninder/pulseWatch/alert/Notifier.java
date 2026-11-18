package com.karaninder.pulseWatch.alert;

/**
 * One way of delivering an alert. AlertService knows only this interface, so adding Slack later means
 * writing one new @Component that implements it, with no change to the code that sends alerts.
 */
public interface Notifier {

	AlertChannel channel();

	/** A channel can be switched off by configuration (e.g. no webhook URL set). */
	boolean isEnabled();

	/** @throws NotificationException if the delivery failed */
	void send(AlertMessage message);
}
