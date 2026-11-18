package com.karaninder.pulseWatch.check;

/**
 * The final outcome of a check plus how many HTTP attempts it took to get there.
 * Only the LAST attempt's outcome is kept: if attempt 1 timed out and attempt 2 returned 200,
 * the monitor is up (attempts = 2).
 */
public record RetriedOutcome(CheckOutcome outcome, int attempts) {
}
