package com.karaninder.pulseWatch.check;

import com.karaninder.pulseWatch.incident.IncidentService;
import com.karaninder.pulseWatch.monitor.MonitorMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckRunnerTest {

	@Mock
	RetryingChecker checker;

	@Mock
	CheckResultRepository repository;

	@Mock
	IncidentService incidentService;

	@Mock
	ApplicationEventPublisher events;

	@Mock
	CheckMetrics metrics;

	private final CheckTarget target = new CheckTarget(7L, "https://example.com", MonitorMethod.GET, 200, 5000, 60, 4, null);

	private CheckRunner runner() {
		return new CheckRunner(checker, repository, incidentService, events, metrics);
	}

	private CheckTarget targetWithLatencyThreshold(int thresholdMs) {
		return new CheckTarget(7L, "https://example.com", MonitorMethod.GET, 200, 5000, 60, 4, thresholdMs);
	}

	@Test
	void savesTheFinalOutcomeAndHowManyAttemptsItTook() {
		when(checker.check(target)).thenReturn(new RetriedOutcome(CheckOutcome.responded(500, 42, 200), 3));
		when(repository.save(any(CheckResult.class))).thenAnswer(inv -> inv.getArgument(0));

		runner().run(target);

		ArgumentCaptor<CheckResult> saved = ArgumentCaptor.forClass(CheckResult.class);
		verify(repository).save(saved.capture());
		CheckResult row = saved.getValue();
		assertThat(row.getMonitorId()).isEqualTo(7L);
		assertThat(row.getStatusCode()).isEqualTo(500);
		assertThat(row.getLatencyMs()).isEqualTo(42);
		assertThat(row.isSuccess()).isFalse();
		assertThat(row.getError()).isEqualTo("Expected status 200 but got 500");
		assertThat(row.getCheckedAt()).isNotNull();
		assertThat(row.getAttemptCount()).isEqualTo(3);
	}

	@Test
	void handsTheSavedResultAndTheMonitorsThresholdToTheIncidentService() {
		when(checker.check(target)).thenReturn(new RetriedOutcome(CheckOutcome.failed(10, "boom"), 1));
		when(repository.save(any(CheckResult.class))).thenAnswer(inv -> inv.getArgument(0));

		CheckResult result = runner().run(target);

		verify(incidentService).evaluate(result, 4);
	}

	@Test
	void aDuplicateIncidentRejectedByTheDatabaseIsNotAnError() {
		when(checker.check(target)).thenReturn(new RetriedOutcome(CheckOutcome.failed(10, "boom"), 1));
		when(repository.save(any(CheckResult.class))).thenAnswer(inv -> inv.getArgument(0));
		doThrow(new DataIntegrityViolationException("uq_incidents_one_open_per_monitor"))
				.when(incidentService).evaluate(any(CheckResult.class), anyInt());

		assertThatCode(() -> runner().run(target)).doesNotThrowAnyException();
	}

	// ---- slow response announcements ----

	@Test
	void aSuccessfulButSlowCheckPublishesASlowResponseEvent() {
		CheckTarget target = targetWithLatencyThreshold(300);
		when(checker.check(target)).thenReturn(new RetriedOutcome(CheckOutcome.responded(200, 450, 200), 1));
		when(repository.save(any(CheckResult.class))).thenAnswer(inv -> inv.getArgument(0));

		CheckResult result = runner().run(target);

		verify(events).publishEvent(new SlowResponseDetected(7L, 450, 300, result.getCheckedAt()));
	}

	@Test
	void aFastCheckPublishesNothing() {
		CheckTarget target = targetWithLatencyThreshold(300);
		when(checker.check(target)).thenReturn(new RetriedOutcome(CheckOutcome.responded(200, 300, 200), 1)); // equal is not slower
		when(repository.save(any(CheckResult.class))).thenAnswer(inv -> inv.getArgument(0));

		runner().run(target);

		verifyNoInteractions(events);
	}

	@Test
	void aMonitorWithoutAThresholdNeverPublishes() {
		when(checker.check(target)).thenReturn(new RetriedOutcome(CheckOutcome.responded(200, 9000, 200), 1));
		when(repository.save(any(CheckResult.class))).thenAnswer(inv -> inv.getArgument(0));

		runner().run(target); // target has latencyThresholdMs = null

		verifyNoInteractions(events);
	}

	@Test
	void aFailedCheckIsNotAlsoReportedAsSlow() {
		CheckTarget target = targetWithLatencyThreshold(300);
		when(checker.check(target)).thenReturn(new RetriedOutcome(CheckOutcome.failed(5000, "Timed out after 5000 ms"), 3));
		when(repository.save(any(CheckResult.class))).thenAnswer(inv -> inv.getArgument(0));

		runner().run(target);

		verify(events, never()).publishEvent(any(SlowResponseDetected.class));
	}
}
