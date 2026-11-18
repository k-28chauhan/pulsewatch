package com.karaninder.pulseWatch.incident;

import com.karaninder.pulseWatch.common.PageResponse;
import com.karaninder.pulseWatch.monitor.MonitorNotFoundException;
import com.karaninder.pulseWatch.monitor.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentQueryServiceTest {

	private static final long USER = 7L;
	private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");

	@Mock
	IncidentRepository incidents;

	@Mock
	MonitorRepository monitors;

	private IncidentQueryService service() {
		return new IncidentQueryService(incidents, monitors, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private Pageable requestedPage() {
		ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
		verify(incidents).findByMonitorId(org.mockito.ArgumentMatchers.eq(5L), captor.capture());
		return captor.getValue();
	}

	@Test
	void historyIsRequestedNewestFirst() {
		when(monitors.existsByIdAndUserId(5L, USER)).thenReturn(true);
		when(incidents.findByMonitorId(org.mockito.ArgumentMatchers.eq(5L), any(Pageable.class))).thenReturn(Page.empty());

		service().history(USER, 5L, 2, 10);

		assertThat(requestedPage()).isEqualTo(PageRequest.of(2, 10, Sort.by(Sort.Direction.DESC, "startedAt")));
	}

	@Test
	void outOfRangeInputIsClampedNotRejected() {
		when(monitors.existsByIdAndUserId(5L, USER)).thenReturn(true);
		when(incidents.findByMonitorId(org.mockito.ArgumentMatchers.eq(5L), any(Pageable.class))).thenReturn(Page.empty());

		service().history(USER, 5L, -3, 100_000);

		assertThat(requestedPage().getPageNumber()).isZero();
		assertThat(requestedPage().getPageSize()).isEqualTo(100);
	}

	@Test
	void aSizeOfZeroBecomesOne() {
		when(monitors.existsByIdAndUserId(5L, USER)).thenReturn(true);
		when(incidents.findByMonitorId(org.mockito.ArgumentMatchers.eq(5L), any(Pageable.class))).thenReturn(Page.empty());

		service().history(USER, 5L, 0, 0);

		assertThat(requestedPage().getPageSize()).isEqualTo(1);
	}

	@Test
	void incidentsAreMappedWithTheirDurationAndTheOpenOneUsesTheClock() {
		Incident resolved = Incident.open(5L, NOW.minusSeconds(900), "boom");
		resolved.resolve(NOW.minusSeconds(600));
		Incident open = Incident.open(5L, NOW.minusSeconds(120), "again");
		when(monitors.existsByIdAndUserId(5L, USER)).thenReturn(true);
		when(incidents.findByMonitorId(org.mockito.ArgumentMatchers.eq(5L), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(open, resolved), PageRequest.of(0, 20), 2));

		PageResponse<IncidentResponse> page = service().history(USER, 5L, 0, 20);

		assertThat(page.totalElements()).isEqualTo(2);
		assertThat(page.content().get(0).open()).isTrue();
		assertThat(page.content().get(0).durationSeconds()).isEqualTo(120);
		assertThat(page.content().get(1).open()).isFalse();
		assertThat(page.content().get(1).durationSeconds()).isEqualTo(300);
	}

	@Test
	void anUnknownMonitorIsNotFound() {
		when(monitors.existsByIdAndUserId(99L, USER)).thenReturn(false);

		assertThatThrownBy(() -> service().history(USER, 99L, 0, 20)).isInstanceOf(MonitorNotFoundException.class);
		verifyNoInteractions(incidents);
	}
}
