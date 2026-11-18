package com.karaninder.pulseWatch.incident;

import com.karaninder.pulseWatch.TestcontainersConfig;
import com.karaninder.pulseWatch.auth.User;
import com.karaninder.pulseWatch.auth.UserRepository;
import com.karaninder.pulseWatch.check.CheckOutcome;
import com.karaninder.pulseWatch.check.CheckResult;
import com.karaninder.pulseWatch.check.CheckResultRepository;
import com.karaninder.pulseWatch.monitor.Monitor;
import com.karaninder.pulseWatch.monitor.MonitorMethod;
import com.karaninder.pulseWatch.monitor.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves, on a real PostgreSQL, the guarantee Phase 3 only unit-tested with mocks: the partial unique index
 * allows one OPEN incident per monitor and nothing else. @Transactional rolls every test back, so the database stays clean.
 */
@SpringBootTest(properties = "pulsewatch.check.enabled=false")
@Import(TestcontainersConfig.class)
@Transactional
class IncidentRepositoryTest {

	private static final Instant T0 = Instant.parse("2026-11-09T10:00:00Z");

	@Autowired
	IncidentRepository incidents;

	@Autowired
	MonitorRepository monitors;

	@Autowired
	CheckResultRepository results;

	@Autowired
	UserRepository users;

	private long newMonitor(String name) {
		Long owner = users.saveAndFlush(new User(name + "@example.com", "hash")).getId();
		return monitors.saveAndFlush(new Monitor(owner, name, "https://example.com", MonitorMethod.GET, 200, 60, 5000, null, 3)).getId();
	}

	@Test
	void aSecondOpenIncidentForTheSameMonitorIsRejectedByTheDatabase() {
		long monitor = newMonitor("a");
		incidents.saveAndFlush(Incident.open(monitor, T0, "first"));

		assertThatThrownBy(() -> incidents.saveAndFlush(Incident.open(monitor, T0.plusSeconds(60), "second")))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uq_incidents_one_open_per_monitor");
	}

	@Test
	void aNewIncidentIsAllowedOnceTheOldOneIsResolved() {
		long monitor = newMonitor("b");
		Incident first = incidents.saveAndFlush(Incident.open(monitor, T0, "first"));
		first.resolve(T0.plusSeconds(300));
		incidents.flush();

		incidents.saveAndFlush(Incident.open(monitor, T0.plusSeconds(600), "second")); // no exception

		assertThat(incidents.findByMonitorId(monitor, PageRequest.of(0, 10))).hasSize(2);
	}

	@Test
	void differentMonitorsMayEachHaveAnOpenIncident() {
		long one = newMonitor("c1");
		long two = newMonitor("c2");

		incidents.saveAndFlush(Incident.open(one, T0, "x"));
		incidents.saveAndFlush(Incident.open(two, T0, "y")); // no exception

		assertThat(incidents.findByMonitorIdAndResolvedAtIsNull(one)).isPresent();
		assertThat(incidents.findByMonitorIdAndResolvedAtIsNull(two)).isPresent();
	}

	@Test
	void findsOnlyTheOpenIncident() {
		long monitor = newMonitor("d");
		Incident old = incidents.saveAndFlush(Incident.open(monitor, T0, "old"));
		old.resolve(T0.plusSeconds(60));
		incidents.flush(); // send the UPDATE before the next INSERT, or the unique index still sees an open one
		Incident current = incidents.saveAndFlush(Incident.open(monitor, T0.plusSeconds(600), "current"));

		assertThat(incidents.findByMonitorIdAndResolvedAtIsNull(monitor)).get().isEqualTo(current);
	}

	@Test
	void resolvedBeforeStartedIsRejectedByTheCheckConstraint() {
		long monitor = newMonitor("e");
		Incident incident = incidents.saveAndFlush(Incident.open(monitor, T0, "x"));
		incident.resolve(T0.minusSeconds(1));

		assertThatThrownBy(() -> incidents.flush()).isInstanceOf(Exception.class)
				.hasMessageContaining("chk_incidents_resolved_after_started");
	}

	@Test
	void historyIsPagedNewestFirst() {
		long monitor = newMonitor("f");
		for (int i = 0; i < 5; i++) {
			Incident incident = incidents.saveAndFlush(Incident.open(monitor, T0.plusSeconds(i * 1000L), "n" + i));
			incident.resolve(T0.plusSeconds(i * 1000L + 10)); // resolve each so the unique index allows the next
			incidents.flush();
		}

		Page<Incident> page = incidents.findByMonitorId(monitor, PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "startedAt")));

		assertThat(page.getTotalElements()).isEqualTo(5);
		assertThat(page.getTotalPages()).isEqualTo(3);
		assertThat(page.getContent()).extracting(Incident::getError).containsExactly("n4", "n3");
	}

	@Test
	void theNewestNCheckResultsComeBackNewestFirst() {
		long monitor = newMonitor("g");
		for (int i = 0; i < 5; i++) {
			results.save(new CheckResult(monitor, T0.plusSeconds(i * 60L), CheckOutcome.failed(10, "e" + i), 1));
		}
		results.flush();

		List<CheckResult> newestThree = results.findByMonitorIdOrderByCheckedAtDesc(monitor, PageRequest.of(0, 3));

		assertThat(newestThree).extracting(CheckResult::getError).containsExactly("e4", "e3", "e2");
	}
}
