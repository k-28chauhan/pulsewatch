package com.karaninder.pulseWatch.check;

import java.time.Duration;

/**
 * "Wait for a while" as an interface. Production uses Thread.sleep; tests pass a recording fake,
 * so a test of 3 retries does not really wait 1.5 seconds.
 * A @FunctionalInterface has exactly one method, so a lambda can implement it.
 */
@FunctionalInterface
public interface Sleeper {

	void sleep(Duration duration) throws InterruptedException;
}
