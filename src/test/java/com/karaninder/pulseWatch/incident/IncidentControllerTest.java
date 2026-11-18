package com.karaninder.pulseWatch.incident;

import com.karaninder.pulseWatch.auth.SecurityConfig;
import com.karaninder.pulseWatch.common.PageResponse;
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

@WebMvcTest(IncidentController.class)
@Import(SecurityConfig.class)
class IncidentControllerTest {

	private static final long USER = 7L;

	@MockitoBean
	JwtDecoder jwtDecoder; // required by the resource-server configuration, unused because of jwt()

	private static org.springframework.test.web.servlet.request.RequestPostProcessor loggedIn() {
		return jwt().jwt(token -> token.subject(String.valueOf(USER)));
	}

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	IncidentQueryService service;

	@Test
	void returnsAPageWithPagingMetadata() throws Exception {
		IncidentResponse incident = new IncidentResponse(1L, 5L, Instant.parse("2026-11-09T10:00:00Z"),
				Instant.parse("2026-11-09T10:05:00Z"), false, 300, "boom");
		when(service.history(USER, 5L, 1, 2)).thenReturn(new PageResponse<>(List.of(incident), 1, 2, 3, 2));

		mockMvc.perform(get("/api/monitors/5/incidents").with(loggedIn()).param("page", "1").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(1))
				.andExpect(jsonPath("$.content[0].durationSeconds").value(300))
				.andExpect(jsonPath("$.content[0].open").value(false))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void pageAndSizeHaveDefaults() throws Exception {
		when(service.history(USER, 5L, 0, 20)).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

		mockMvc.perform(get("/api/monitors/5/incidents").with(loggedIn())).andExpect(status().isOk());

		verify(service).history(USER, 5L, 0, 20);
	}

	@Test
	void anUnknownMonitorIs404() throws Exception {
		when(service.history(USER, 99L, 0, 20)).thenThrow(new MonitorNotFoundException(99L));

		mockMvc.perform(get("/api/monitors/99/incidents").with(loggedIn())).andExpect(status().isNotFound());
	}

	@Test
	void aNonNumericPageIs400() throws Exception {
		mockMvc.perform(get("/api/monitors/5/incidents").with(loggedIn()).param("page", "abc")).andExpect(status().isBadRequest());
	}

	@Test
	void withoutATokenTheHistoryIsNotReachable() throws Exception {
		mockMvc.perform(get("/api/monitors/5/incidents")).andExpect(status().isUnauthorized());
	}
}
