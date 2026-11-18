package com.karaninder.pulseWatch.check;

import org.springframework.stereotype.Component;

/**
 * Wraps HttpChecker in a hand-written retry loop: try, and while it fails and attempts remain,
 * wait (exponential backoff) and try again. Blocking in sleep() is fine here because every check
 * runs on its own virtual thread.
 */
@Component
public class RetryingChecker {

	private final HttpChecker httpChecker;
	private final RetryPolicy policy;
	private final Sleeper sleeper;

	public RetryingChecker(HttpChecker httpChecker, RetryPolicy policy, Sleeper sleeper) {
		this.httpChecker = httpChecker;
		this.policy = policy;
		this.sleeper = sleeper;
	}

	public RetriedOutcome check(CheckTarget target) {
		int allowedAttempts = policy.attemptsThatFit(target.timeoutMs(), target.intervalSeconds() * 1000L);

		int attempts = 1;
		CheckOutcome outcome = httpChecker.check(target);
		while (!outcome.success() && attempts < allowedAttempts) {
			try {
				sleeper.sleep(policy.backoffBefore(attempts + 1));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt(); // restore the flag, stop retrying (app is shutting down)
				break;
			}
			outcome = httpChecker.check(target);
			attempts++;
		}
		return new RetriedOutcome(outcome, attempts);
	}
}
