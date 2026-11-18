package com.karaninder.pulseWatch.stats;

import com.karaninder.pulseWatch.TestcontainersConfig;
import com.karaninder.pulseWatch.incident.Incident;
import com.karaninder.pulseWatch.incident.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole stack for phase 5 with nothing mocked: HTTP request, controller, service, SQL on a real
 * PostgreSQL. This is the "done when" of the phase: ONE call returns what a monitor's detail page needs.
 */
@SpringBootTest(properties = "pulsewatch.check.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class StatsApiTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	IncidentRepository incidents;

	@Autowired
	StatsMaintenanceService maintenance;

	private StatsTestData data;
	private long owner; // the logged-in user of every request in this class

	@BeforeEach
	void cleanDatabase() {
		data = new StatsTestData(jdbc);
		data.deleteEverything();
		owner = data.user("owner@example.com");
	}

	/** A verified token for the owner, as the resource server would produce it from a real login. */
	private org.springframework.test.web.servlet.request.RequestPostProcessor asOwner() {
		return jwt().jwt(token -> token.subject(String.valueOf(owner)));
	}

	@Test
	void oneCallReturnsEverythingTheDetailPageNeeds() throws Exception {
		Instant now = Instant.now();
		long monitor = data.monitorOf(owner, "detail");
		for (int i = 1; i <= 8; i++) {                       // 8 successes, latencies 100..800 ms
			data.success(monitor, now.minus(i * 10L, ChronoUnit.MINUTES), i * 100);
		}
		data.failure(monitor, now.minus(95, ChronoUnit.MINUTES), 5000);
		data.failure(monitor, now.minus(105, ChronoUnit.MINUTES), 5000);
		data.success(monitor, now.minus(3, ChronoUnit.DAYS), 100); // visible in the 7 d window only
		incidents.saveAndFlush(Incident.open(monitor, now.minus(100, ChronoUnit.MINUTES), "Timed out"));

		mockMvc.perform(get("/api/monitors/" + monitor + "/stats").with(asOwner()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.monitorId").value(monitor))
				.andExpect(jsonPath("$.uptime24h.totalChecks").value(10))
				.andExpect(jsonPath("$.uptime24h.failedChecks").value(2))
				.andExpect(jsonPath("$.uptime24h.uptimePercent").value(80.0))
				.andExpect(jsonPath("$.uptime24h.avgLatencyMs").value(450.0))
				.andExpect(jsonPath("$.uptime7d.totalChecks").value(11))
				.andExpect(jsonPath("$.uptime30d.totalChecks").value(11))
				.andExpect(jsonPath("$.latency24h.p50").value(450.0))
				.andExpect(jsonPath("$.latency24h.max").value(800))
				.andExpect(jsonPath("$.errorRate24h").isNotEmpty())
				.andExpect(jsonPath("$.openIncident.open").value(true))
				.andExpect(jsonPath("$.openIncident.error").value("Timed out"));
	}

	@Test
	void aMonitorWithoutChecksReportsNoDataInsteadOfFalseNumbers() throws Exception {
		long monitor = data.monitorOf(owner, "fresh");

		mockMvc.perform(get("/api/monitors/" + monitor + "/stats").with(asOwner()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.uptime24h.uptimePercent").isEmpty())
				.andExpect(jsonPath("$.uptime24h.totalChecks").value(0))
				.andExpect(jsonPath("$.latency24h.p95").isEmpty())
				.andExpect(jsonPath("$.errorRate24h").isEmpty())
				.andExpect(jsonPath("$.openIncident").isEmpty());
	}

	@Test
	void thirtyDayUptimeSurvivesTheNightlyCleanupOfRawRows() throws Exception {
		Instant now = Instant.now();
		long monitor = data.monitorOf(owner, "history");
		Instant oldHour = now.minus(12, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
		for (int minute = 1; minute <= 5; minute++) {         // 5 successes, one hour, 12 days ago
			data.success(monitor, oldHour.plus(minute * 10L, ChronoUnit.MINUTES), 100);
		}
		data.failure(monitor, oldHour.plus(55, ChronoUnit.MINUTES), 5000);
		data.success(monitor, now.minus(5, ChronoUnit.MINUTES), 200);
		data.success(monitor, now.minus(4, ChronoUnit.MINUTES), 200);

		StatsMaintenanceService.Result result = maintenance.run(now);

		assertThat(result.rawRowsDeleted()).isEqualTo(6);                       // the 12-day-old rows are gone...
		assertThat(data.count("check_results")).isEqualTo(2);
		mockMvc.perform(get("/api/monitors/" + monitor + "/stats").with(asOwner()))              // ...but still counted, via the hourly rollup
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.uptime30d.totalChecks").value(8))        // 6 old + 2 recent, none counted twice
				.andExpect(jsonPath("$.uptime30d.failedChecks").value(1))
				.andExpect(jsonPath("$.uptime24h.totalChecks").value(2));
	}

	@Test
	void errorRateBucketsFollowTheRequestedWindow() throws Exception {
		Instant now = Instant.now();
		long monitor = data.monitorOf(owner, "chart");
		data.success(monitor, now.minus(30, ChronoUnit.MINUTES), 100);
		data.failure(monitor, now.minus(20, ChronoUnit.MINUTES), 100);

		mockMvc.perform(get("/api/monitors/" + monitor + "/error-rate").with(asOwner()).param("window", "7d"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].totalChecks").isNumber());
		mockMvc.perform(get("/api/monitors/" + monitor + "/error-rate").with(asOwner()).param("window", "1y"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/monitors/999999/error-rate").with(asOwner()))
				.andExpect(status().isNotFound());
	}

	@Test
	void incidentHistoryIsPaginatedNewestFirst() throws Exception {
		Instant now = Instant.now();
		long monitor = data.monitorOf(owner, "history-pages");
		for (int i = 0; i < 3; i++) {
			Incident incident = Incident.open(monitor, now.minus(10 - i * 3L, ChronoUnit.HOURS), "outage " + i);
			incident.resolve(now.minus(10 - i * 3L, ChronoUnit.HOURS).plusSeconds(60)); // resolved before saving
			incidents.saveAndFlush(incident);
		}

		mockMvc.perform(get("/api/monitors/" + monitor + "/incidents").with(asOwner()).param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].error").value("outage 2"))
				.andExpect(jsonPath("$.content[0].durationSeconds").value(60));
		mockMvc.perform(get("/api/monitors/" + monitor + "/incidents").with(asOwner()).param("size", "2").param("page", "1"))
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].error").value("outage 0"));
		mockMvc.perform(get("/api/monitors/999999/incidents").with(asOwner())).andExpect(status().isNotFound());
	}
}
