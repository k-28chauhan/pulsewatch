package com.karaninder.pulseWatch.stats;

import com.karaninder.pulseWatch.auth.SecurityConfig;
import com.karaninder.pulseWatch.monitor.MonitorNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatsController.class)
@Import(SecurityConfig.class)
class StatsControllerTest {

	private static final long USER = 7L;

	@MockitoBean
	JwtDecoder jwtDecoder; // required by the resource-server configuration, unused because of jwt()

	private static org.springframework.test.web.servlet.request.RequestPostProcessor loggedIn() {
		return jwt().jwt(token -> token.subject(String.valueOf(USER)));
	}

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	StatsService service;

	@Test
	void statsAreReturnedAsOneJsonDocument() throws Exception {
		Instant now = Instant.parse("2026-11-10T12:30:00Z");
		UptimeStats uptime = new UptimeStats(99.5, 200, 1, 123.4);
		when(service.stats(USER, 5L)).thenReturn(new StatsResponse(5L, now, uptime, uptime, uptime,
				new LatencyStats(100.0, 250.0, 300.0, 400), new LatencyStats(null, null, null, null),
				List.of(ErrorRateBucket.of(Instant.parse("2026-11-10T11:00:00Z"), 60, 3)), null));

		mockMvc.perform(get("/api/monitors/5/stats").with(loggedIn()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.monitorId").value(5))
				.andExpect(jsonPath("$.uptime24h.uptimePercent").value(99.5))
				.andExpect(jsonPath("$.latency24h.p95").value(250.0))
				.andExpect(jsonPath("$.latency7d.p50").isEmpty())
				.andExpect(jsonPath("$.errorRate24h[0].errorRate").value(0.05))
				.andExpect(jsonPath("$.openIncident").isEmpty());
	}

	@Test
	void errorRateDefaultsToTheLast24Hours() throws Exception {
		when(service.errorRate(USER, 5L, StatsWindow.LAST_24H)).thenReturn(List.of());

		mockMvc.perform(get("/api/monitors/5/error-rate").with(loggedIn()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		verify(service).errorRate(USER, 5L, StatsWindow.LAST_24H);
	}

	@Test
	void errorRateAcceptsAWindowParameter() throws Exception {
		when(service.errorRate(USER, 5L, StatsWindow.LAST_7D)).thenReturn(List.of());

		mockMvc.perform(get("/api/monitors/5/error-rate").with(loggedIn()).param("window", "7d")).andExpect(status().isOk());

		verify(service).errorRate(USER, 5L, StatsWindow.LAST_7D);
	}

	@Test
	void anUnknownWindowIs400WithAHelpfulDetail() throws Exception {
		mockMvc.perform(get("/api/monitors/5/error-rate").with(loggedIn()).param("window", "1y"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Unknown window '1y'; use one of 24h, 7d, 30d"));
	}

	@Test
	void anUnknownMonitorIs404() throws Exception {
		when(service.stats(USER, 99L)).thenThrow(new MonitorNotFoundException(99L));

		mockMvc.perform(get("/api/monitors/99/stats").with(loggedIn()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Monitor 99 not found"));
	}

	@Test
	void withoutATokenTheStatsAreNotReachable() throws Exception {
		mockMvc.perform(get("/api/monitors/5/stats"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void theOverviewListsMonitorsWithTheirStatus() throws Exception {
		when(service.overview(USER)).thenReturn(List.of(new MonitorOverview(5L, "Homepage", "https://example.com",
				MonitorStatus.UP, true, 60, Instant.parse("2026-11-10T12:00:00Z"), 123, 99.5)));

		mockMvc.perform(get("/api/monitors/overview").with(loggedIn()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(5))
				.andExpect(jsonPath("$[0].status").value("UP"))
				.andExpect(jsonPath("$[0].lastLatencyMs").value(123))
				.andExpect(jsonPath("$[0].uptime24h").value(99.5));
	}

	@Test
	void theLatencySeriesIsReturnedPerBucket() throws Exception {
		when(service.latencySeries(USER, 5L, StatsWindow.LAST_7D)).thenReturn(List.of(
				new LatencyBucket(Instant.parse("2026-11-10T10:00:00Z"), 120.0, 340.5, 4)));

		mockMvc.perform(get("/api/monitors/5/latency").param("window", "7d").with(loggedIn()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].p50").value(120.0))
				.andExpect(jsonPath("$[0].p95").value(340.5))
				.andExpect(jsonPath("$[0].checks").value(4));
	}

	@Test
	void aThirtyDayLatencySeriesIs400() throws Exception {
		when(service.latencySeries(USER, 5L, StatsWindow.LAST_30D)).thenThrow(InvalidStatsWindowException.noLatencySeries(StatsWindow.LAST_30D));

		mockMvc.perform(get("/api/monitors/5/latency").param("window", "30d").with(loggedIn()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("24h and 7d only")));
	}

	@Test
	void theOverviewNeedsAToken() throws Exception {
		mockMvc.perform(get("/api/monitors/overview")).andExpect(status().isUnauthorized());
	}
}
