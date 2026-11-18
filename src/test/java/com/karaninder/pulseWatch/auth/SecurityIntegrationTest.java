package com.karaninder.pulseWatch.auth;

import com.jayway.jsonpath.JsonPath;
import com.karaninder.pulseWatch.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Done when: two users can't see each other's monitors." The whole stack, nothing mocked: real BCrypt,
 * real signed tokens, the real security filter chain, real PostgreSQL. Tokens come from the register endpoint,
 * exactly as a client would get them.
 */
@SpringBootTest(properties = "pulsewatch.check.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class SecurityIntegrationTest {

	private static final String MONITOR_JSON = """
			{"name": "Ann's site", "url": "https://example.com"}
			""";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@BeforeEach
	void cleanDatabase() {
		jdbc.sql("DELETE FROM users").update(); // cascades to monitors and everything below them
	}

	private String register(String email) throws Exception {
		String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"" + email + "\", \"password\": \"correct horse battery\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private long createMonitor(String token) throws Exception {
		String body = mockMvc.perform(post("/api/monitors").header("Authorization", bearer(token))
						.contentType(MediaType.APPLICATION_JSON).content(MONITOR_JSON))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	// ---- the "done when" ----

	@Test
	void twoUsersCannotSeeOrTouchEachOthersMonitors() throws Exception {
		String ann = register("ann@example.com");
		String bob = register("bob@example.com");
		long annsMonitor = createMonitor(ann);

		// Ann sees hers
		mockMvc.perform(get("/api/monitors").header("Authorization", bearer(ann)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
		mockMvc.perform(get("/api/monitors/" + annsMonitor).header("Authorization", bearer(ann)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Ann's site"));

		// the overview (the UI's list) is scoped the same way
		mockMvc.perform(get("/api/monitors/overview").header("Authorization", bearer(ann)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].status").value("PENDING"));
		mockMvc.perform(get("/api/monitors/overview").header("Authorization", bearer(bob)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

		// Bob sees nothing of it: not in his list, and every direct access is 404 (not 403: existence is not revealed)
		mockMvc.perform(get("/api/monitors").header("Authorization", bearer(bob)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/monitors/" + annsMonitor).header("Authorization", bearer(bob)))
				.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/monitors/" + annsMonitor).header("Authorization", bearer(bob))
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"hijacked\", \"url\": \"https://evil.example\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/api/monitors/" + annsMonitor + "/pause").header("Authorization", bearer(bob)))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/monitors/" + annsMonitor).header("Authorization", bearer(bob)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/monitors/" + annsMonitor + "/stats").header("Authorization", bearer(bob)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/monitors/" + annsMonitor + "/error-rate").header("Authorization", bearer(bob)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/monitors/" + annsMonitor + "/incidents").header("Authorization", bearer(bob)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/monitors/" + annsMonitor + "/latency").header("Authorization", bearer(bob)))
				.andExpect(status().isNotFound());

		// and nothing Bob tried changed Ann's monitor
		mockMvc.perform(get("/api/monitors/" + annsMonitor).header("Authorization", bearer(ann)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Ann's site"))
				.andExpect(jsonPath("$.enabled").value(true));
		// Ann can use the stats of her own monitor
		mockMvc.perform(get("/api/monitors/" + annsMonitor + "/stats").header("Authorization", bearer(ann)))
				.andExpect(status().isOk());
	}

	@Test
	void monitorsAimedAtTheServersOwnNetworkAreRefused() throws Exception {
		String ann = register("ssrf@example.com");

		for (String url : new String[]{"http://169.254.169.254/latest/meta-data/", "http://localhost:5432/", "http://10.0.0.5/admin", "http://[::1]/"}) {
			mockMvc.perform(post("/api/monitors").header("Authorization", bearer(ann))
							.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"x\", \"url\": \"" + url + "\"}"))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors.url").value(org.hamcrest.Matchers.containsString("private or internal")));
		}
		mockMvc.perform(get("/api/monitors").header("Authorization", bearer(ann)))
				.andExpect(jsonPath("$.length()").value(0)); // none were saved
	}

	// ---- who is let in at all ----

	@Test
	void protectedEndpointsRejectMissingMalformedAndForgedTokens() throws Exception {
		mockMvc.perform(get("/api/monitors")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/monitors").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/monitors").header("Authorization", "Basic YW5uOnB3")).andExpect(status().isUnauthorized());
	}

	@Test
	void publicEndpointsNeedNoToken() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("bearerAuth")));
	}

	@Test
	void guessingOneAccountsPasswordIsStoppedAfterTenTries() throws Exception {
		register("victim@example.com");

		for (int i = 0; i < 10; i++) {
			mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
							.content("{\"email\": \"victim@example.com\", \"password\": \"guess number " + i + "\"}"))
					.andExpect(status().isUnauthorized());
		}
		// the 11th try is refused before the password is even looked at, even though it is the CORRECT one
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"victim@example.com\", \"password\": \"correct horse battery\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("Retry-After"));
	}

	// ---- registration and login with the real hashing ----

	@Test
	void registerThenLoginWorksAndThePasswordIsStoredAsABcryptHash() throws Exception {
		register("Carol@Example.com");

		String stored = jdbc.sql("SELECT password_hash FROM users WHERE email = 'carol@example.com'").query(String.class).single();
		assertThat(stored).startsWith("$2").doesNotContain("correct horse");

		String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"CAROL@example.com\", \"password\": \"correct horse battery\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(login, "$.token");
		mockMvc.perform(get("/api/monitors").header("Authorization", bearer(token))).andExpect(status().isOk());
	}

	@Test
	void loginFailsWithTheSameAnswerForAWrongPasswordAndAnUnknownEmail() throws Exception {
		register("dave@example.com");

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"dave@example.com\", \"password\": \"wrong password!\"}"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Invalid email or password"));
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"nobody@example.com\", \"password\": \"wrong password!\"}"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Invalid email or password"));
	}

	@Test
	void registeringTheSameEmailTwiceIgnoringCaseIs409() throws Exception {
		register("erin@example.com");

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\": \"ERIN@example.com\", \"password\": \"correct horse battery\"}"))
				.andExpect(status().isConflict());
	}
}
