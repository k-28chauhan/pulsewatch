package com.karaninder.pulseWatch.incident;

import com.karaninder.pulseWatch.check.CheckOutcome;
import com.karaninder.pulseWatch.check.CheckResult;
import com.karaninder.pulseWatch.check.CheckResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** One test per state change of the incident state machine. */
@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

	private static final long MONITOR = 5L;
	private static final Instant T0 = Instant.parse("2026-11-09T10:00:00Z");

	@Mock
	IncidentRepository incidents;

	@Mock
	CheckResultRepository results;

	@Mock
	ApplicationEventPublisher events;

	private IncidentService service() {
		return new IncidentService(incidents, results, events);
	}

	/** The database normally assigns the id on INSERT; this fake does it for the mock repository. */
	private void saveAssignsId(long id) {
		when(incidents.saveAndFlush(any(Incident.class))).thenAnswer(inv -> {
			Incident incident = inv.getArgument(0);
			ReflectionTestUtils.setField(incident, "id", id);
			return incident;
		});
	}

	private CheckResult failureAt(int secondsAfterT0) {
		return new CheckResult(MONITOR, T0.plusSeconds(secondsAfterT0), CheckOutcome.failed(5000, "Timed out"), 3);
	}

	private CheckResult successAt(int secondsAfterT0) {
		return new CheckResult(MONITOR, T0.plusSeconds(secondsAfterT0), CheckOutcome.responded(200, 30, 200), 1);
	}

	// ---- closed -> open ----

	@Test
	void opensAnIncidentWhenTheLastNChecksAllFailed() {
		CheckResult latest = failureAt(120);
		when(incidents.findByMonitorIdAndResolvedAtIsNull(MONITOR)).thenReturn(Optional.empty());
		// newest first, as the repository returns them
		when(results.findByMonitorIdOrderByCheckedAtDesc(MONITOR, PageRequest.of(0, 3)))
				.thenReturn(List.of(latest, failureAt(60), failureAt(0)));
		saveAssignsId(42L);

		IncidentTransition transition = service().evaluate(latest, 3);

		assertThat(transition).isEqualTo(IncidentTransition.OPENED);
		ArgumentCaptor<Incident> saved = ArgumentCaptor.forClass(Incident.class);
		verify(incidents).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getMonitorId()).isEqualTo(MONITOR);
		assertThat(saved.getValue().getStartedAt()).isEqualTo(T0); // the FIRST failure of the streak
		assertThat(saved.getValue().getError()).isEqualTo("Timed out");
		assertThat(saved.getValue().isOpen()).isTrue();
		verify(events).publishEvent(new IncidentOpened(42L, MONITOR, T0, "Timed out"));
	}

	@Test
	void doesNotOpenWhileTheFailureStreakIsShorterThanTheThreshold() {
		CheckResult latest = failureAt(60);
		when(incidents.findByMonitorIdAndResolvedAtIsNull(MONITOR)).thenReturn(Optional.empty());
		when(results.findByMonitorIdOrderByCheckedAtDesc(MONITOR, PageRequest.of(0, 3)))
				.thenReturn(List.of(latest, successAt(0)));

		assertThat(service().evaluate(latest, 3)).isEqualTo(IncidentTransition.NONE);
		verify(incidents, never()).saveAndFlush(any());
		verifyNoInteractions(events);
	}

	@Test
	void doesNotOpenWhenThereIsNotEnoughHistoryYet() {
		CheckResult latest = failureAt(0);
		when(incidents.findByMonitorIdAndResolvedAtIsNull(MONITOR)).thenReturn(Optional.empty());
		when(results.findByMonitorIdOrderByCheckedAtDesc(MONITOR, PageRequest.of(0, 3)))
				.thenReturn(List.of(latest)); // brand-new monitor: only 1 result exists

		assertThat(service().evaluate(latest, 3)).isEqualTo(IncidentTransition.NONE);
		verify(incidents, never()).saveAndFlush(any());
	}

	@Test
	void aSuccessInsideTheWindowBreaksTheStreak() {
		CheckResult latest = failureAt(120);
		when(incidents.findByMonitorIdAndResolvedAtIsNull(MONITOR)).thenReturn(Optional.empty());
		when(results.findByMonitorIdOrderByCheckedAtDesc(MONITOR, PageRequest.of(0, 3)))
				.thenReturn(List.of(latest, successAt(60), failureAt(0))); // fail, ok, fail: not "in a row"

		assertThat(service().evaluate(latest, 3)).isEqualTo(IncidentTransition.NONE);
		verify(incidents, never()).saveAndFlush(any());
	}

	@Test
	void aThresholdOfOneOpensOnTheFirstFailure() {
		CheckResult latest = failureAt(0);
		when(incidents.findByMonitorIdAndResolvedAtIsNull(MONITOR)).thenReturn(Optional.empty());
		when(results.findByMonitorIdOrderByCheckedAtDesc(MONITOR, PageRequest.of(0, 1))).thenReturn(List.of(latest));
		saveAssignsId(1L);

		assertThat(service().evaluate(latest, 1)).isEqualTo(IncidentTransition.OPENED);
	}

	// ---- open stays open (deduplication) ----

	@Test
	void anotherFailureWhileAnIncidentIsOpenDoesNothing() {
		Incident open = Incident.open(MONITOR, T0, "Timed out");
		when(incidents.findByMonitorIdAndResolvedAtIsNull(MONITOR)).thenReturn(Optional.of(open));

		IncidentTransition transition = service().evaluate(failureAt(300), 3);

		assertThat(transition).isEqualTo(IncidentTransition.NONE);
		assertThat(open.isOpen()).isTrue();
		verify(incidents, never()).saveAndFlush(any());
		verifyNoInteractions(results); // no need to even look at the history
		verifyNoInteractions(events);  // no second alert for the same outage
	}

	// ---- open -> resolved ----

	@Test
	void theFirstSuccessResolvesTheOpenIncident() {
		Incident open = Incident.open(MONITOR, T0, "Timed out");
		CheckResult recovery = successAt(600);
		when(incidents.findByMonitorIdAndResolvedAtIsNull(MONITOR)).thenReturn(Optional.of(open));

		IncidentTransition transition = service().evaluate(recovery, 3);

		assertThat(transition).isEqualTo(IncidentTransition.RESOLVED);
		assertThat(open.isOpen()).isFalse();
		assertThat(open.getResolvedAt()).isEqualTo(recovery.getCheckedAt());
		verify(events).publishEvent(new IncidentResolved(null, MONITOR, T0, recovery.getCheckedAt()));
	}

	// ---- healthy stays healthy ----

	@Test
	void aSuccessWithNoOpenIncidentDoesNothing() {
		when(incidents.findByMonitorIdAndResolvedAtIsNull(MONITOR)).thenReturn(Optional.empty());

		assertThat(service().evaluate(successAt(0), 3)).isEqualTo(IncidentTransition.NONE);
		verify(incidents, never()).saveAndFlush(any());
		verifyNoInteractions(results);
		verifyNoInteractions(events);
	}
}
