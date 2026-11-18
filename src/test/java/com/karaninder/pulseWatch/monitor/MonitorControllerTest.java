package com.karaninder.pulseWatch.monitor;

import com.karaninder.pulseWatch.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests only the web layer: JSON in/out, validation and error mapping.
 * @WebMvcTest starts controllers + the exception handler, NOT the database;
 * the service is replaced by a Mockito mock. SecurityConfig is imported so the real access rules apply;
 * jwt() fakes an already-verified token for user 7, so no real token or decoder is needed.
 */
@WebMvcTest(MonitorController.class)
@Import(SecurityConfig.class)
class MonitorControllerTest {

	private static final long USER = 7L;

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	MonitorService service;

	@MockitoBean
	JwtDecoder jwtDecoder; // required by the resource-server configuration, unused because of jwt()

	private static org.springframework.test.web.servlet.request.RequestPostProcessor loggedIn() {
		return jwt().jwt(token -> token.subject(String.valueOf(USER)));
	}

	private Monitor monitorWithId(long id) {
		Monitor m = new Monitor(USER, "Homepage", "https://example.com", MonitorMethod.GET, 200, 60, 5000, null, 3);
		ReflectionTestUtils.setField(m, "id", id); // the DB normally assigns the id
		return m;
	}

	@Test
	void createReturns201WithLocationAndBody() throws Exception {
		when(service.create(eq(USER), any(MonitorRequest.class))).thenReturn(monitorWithId(7));

		mockMvc.perform(post("/api/monitors").with(loggedIn())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "Homepage", "url": "https://example.com"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost/api/monitors/7"))
				.andExpect(jsonPath("$.id").value(7))
				.andExpect(jsonPath("$.intervalSeconds").value(60))
				.andExpect(jsonPath("$.enabled").value(true));
	}

	@Test
	void createRejectsInvalidInputAndListsEveryBadField() throws Exception {
		mockMvc.perform(post("/api/monitors").with(loggedIn())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "", "url": "ftp://nope", "intervalSeconds": 5}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.name").exists())
				.andExpect(jsonPath("$.errors.url").exists())
				.andExpect(jsonPath("$.errors.intervalSeconds").exists());

		verifyNoInteractions(service); // invalid requests must never reach the service
	}

	@Test
	void createRejectsATimeoutThatIsNotShorterThanTheInterval() throws Exception {
		mockMvc.perform(post("/api/monitors").with(loggedIn())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "x", "url": "https://example.com", "intervalSeconds": 10, "timeoutMs": 10000}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.timeoutMs").value("must be shorter than the check interval (10000 ms)"));

		verifyNoInteractions(service);
	}

	@Test
	void createRejectsUnknownEnumValue() throws Exception {
		mockMvc.perform(post("/api/monitors").with(loggedIn())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "x", "url": "https://example.com", "method": "GTE"}
								"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void getUnknownIdReturns404ProblemDetail() throws Exception {
		when(service.findById(USER, 99L)).thenThrow(new MonitorNotFoundException(99L));

		mockMvc.perform(get("/api/monitors/99").with(loggedIn()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value("Monitor 99 not found"));
	}

	@Test
	void nonNumericIdReturns400() throws Exception {
		mockMvc.perform(get("/api/monitors/abc").with(loggedIn()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void listReturnsAllMonitors() throws Exception {
		when(service.findAll(USER)).thenReturn(List.of(monitorWithId(1), monitorWithId(2)));

		mockMvc.perform(get("/api/monitors").with(loggedIn()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[1].id").value(2));
	}

	@Test
	void deleteReturns204() throws Exception {
		mockMvc.perform(delete("/api/monitors/1").with(loggedIn()))
				.andExpect(status().isNoContent());
	}

	@Test
	void anInternalTargetIsRejectedWithTheReasonOnTheUrlField() throws Exception {
		when(service.create(eq(USER), any(MonitorRequest.class))).thenThrow(new ForbiddenTargetException("169.254.169.254 is a private or internal address"));

		mockMvc.perform(post("/api/monitors").with(loggedIn())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name": "x", "url": "http://169.254.169.254/"}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.url").value("169.254.169.254 is a private or internal address"));
	}

	@Test
	void everyEndpointRequiresAToken() throws Exception {
		mockMvc.perform(get("/api/monitors")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/monitors").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/monitors/1")).andExpect(status().isUnauthorized());

		verifyNoInteractions(service);
	}
}
