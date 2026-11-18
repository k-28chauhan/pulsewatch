package com.karaninder.pulseWatch.check;

import com.karaninder.pulseWatch.monitor.MonitorMethod;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests HttpChecker against a REAL tiny HTTP server started inside the test (the JDK ships one),
 * so no mocks and no internet are needed. Port 0 means "pick any free port".
 */
class HttpCheckerTest {

	private HttpServer server;
	private HttpChecker checker;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/ok", exchange -> respond(exchange, 200));
		server.createContext("/error", exchange -> respond(exchange, 500));
		server.createContext("/missing", exchange -> respond(exchange, 404));
		server.createContext("/slow", exchange -> {
			try {
				Thread.sleep(1500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			respond(exchange, 200);
		});
		server.start();
		checker = new HttpChecker(HttpClient.newHttpClient(), new TargetGuard(new CheckProperties(true))); // the test server lives on localhost
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status) throws IOException {
		exchange.sendResponseHeaders(status, -1); // -1 = no body
		exchange.close();
	}

	private CheckTarget target(String path, int expectedStatus, int timeoutMs) {
		String url = "http://localhost:" + server.getAddress().getPort() + path;
		return new CheckTarget(1L, url, MonitorMethod.GET, expectedStatus, timeoutMs, 60, 3, null);
	}

	@Test
	void expectedStatusIsSuccess() {
		CheckOutcome outcome = checker.check(target("/ok", 200, 2000));

		assertThat(outcome.success()).isTrue();
		assertThat(outcome.statusCode()).isEqualTo(200);
		assertThat(outcome.error()).isNull();
	}

	@Test
	void unexpectedStatusIsAFailureThatKeepsTheStatusCode() {
		CheckOutcome outcome = checker.check(target("/error", 200, 2000));

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.statusCode()).isEqualTo(500);
		assertThat(outcome.error()).isEqualTo("Expected status 200 but got 500");
	}

	@Test
	void a404CountsAsSuccessWhenThatIsWhatTheMonitorExpects() {
		CheckOutcome outcome = checker.check(target("/missing", 404, 2000));

		assertThat(outcome.success()).isTrue();
	}

	@Test
	void slowResponseTimesOutAndHasNoStatusCode() {
		CheckOutcome outcome = checker.check(target("/slow", 200, 300));

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.statusCode()).isNull();
		assertThat(outcome.error()).isEqualTo("Timed out after 300 ms");
		assertThat(outcome.latencyMs()).isBetween(250, 1400); // gave up at ~300 ms, did not wait for 1500
	}

	@Test
	void refusedConnectionIsAFailureNotAnException() {
		CheckTarget deadTarget = target("/ok", 200, 2000);
		server.stop(0); // the port is now closed

		CheckOutcome outcome = checker.check(deadTarget);

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.statusCode()).isNull();
		assertThat(outcome.error()).isNotBlank();
	}

	@Test
	void malformedUrlIsAFailureNotAnException() {
		CheckOutcome outcome = checker.check(new CheckTarget(1L, "http://bad host/", MonitorMethod.GET, 200, 1000, 60, 3, null));

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.error()).isNotBlank();
	}

	@Test
	void anInternalTargetIsRefusedWithoutEverBeingContacted() {
		HttpChecker strict = new HttpChecker(HttpClient.newHttpClient(), new TargetGuard(new CheckProperties(false)));
		int[] hits = {0};
		server.createContext("/spy", exchange -> {
			hits[0]++;
			respond(exchange, 200);
		});

		CheckOutcome outcome = strict.check(target("/spy", 200, 2000)); // http://localhost:<port>/spy

		assertThat(outcome.success()).isFalse();
		assertThat(outcome.error()).startsWith("Target not allowed:");
		assertThat(hits[0]).isZero();
	}
}
