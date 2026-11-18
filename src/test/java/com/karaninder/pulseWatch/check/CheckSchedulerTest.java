package com.karaninder.pulseWatch.check;

import com.karaninder.pulseWatch.monitor.MonitorMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckSchedulerTest {

	@Mock
	CheckScheduleService scheduleService;

	@Mock
	CheckRunner runner;

	// "Runnable::run" is an Executor that runs the task immediately on the calling thread,
	// which makes the test deterministic (no waiting for background threads).
	private final Executor sameThread = Runnable::run;

	@BeforeEach
	void runnerReturnsAResultByDefault() {
		// A bare mock returns null, which the scheduler's log line would trip over.
		// lenient(): tests that never call the runner must not fail for an "unused stub".
		CheckResult ok = new CheckResult(1L, Instant.now(), CheckOutcome.responded(200, 10, 200), 1);
		lenient().when(runner.run(any())).thenReturn(ok);
	}

	private CheckTarget target(long id) {
		return new CheckTarget(id, "https://example.com", MonitorMethod.GET, 200, 5000, 60, 3, null);
	}

	@Test
	void runsOneCheckPerDueMonitor() {
		when(scheduleService.claimDueMonitors(any())).thenReturn(List.of(target(1), target(2)));

		new CheckScheduler(scheduleService, runner, sameThread).tick();

		verify(runner).run(target(1)); // records have equals(), so these match by value
		verify(runner).run(target(2));
	}

	@Test
	void doesNothingWhenNothingIsDue() {
		when(scheduleService.claimDueMonitors(any())).thenReturn(List.of());

		new CheckScheduler(scheduleService, runner, sameThread).tick();

		verify(runner, never()).run(any());
	}

	@Test
	void oneFailingCheckDoesNotStopTheOthers() {
		when(scheduleService.claimDueMonitors(any())).thenReturn(List.of(target(1), target(2)));
		when(runner.run(target(1))).thenThrow(new RuntimeException("database hiccup"));

		new CheckScheduler(scheduleService, runner, sameThread).tick();

		verify(runner).run(target(2)); // still ran, and tick() did not throw
	}
}
