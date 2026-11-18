package com.karaninder.pulseWatch.alert;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Uses a real tiny HTTP server inside the test (port 0 = any free port), like HttpCheckerTest. */
class WebhookNotifierTest {

	private HttpServer server;
	private final AtomicReference<String> receivedBody = new AtomicReference<>();
	private final AtomicReference<String> receivedContentType = new AtomicReference<>();
	private volatile int answerWith = 200;

	private final AlertMessage message = new AlertMessage(AlertType.INCIDENT_OPENED, 1L, 2L, "owner@x.test", "[PulseWatch] DOWN: Homepage", "Line 1\n\"quoted\"");

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		server.createContext("/hook", exchange -> {
			receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
			exchange.sendResponseHeaders(answerWith, -1);
			exchange.close();
		});
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private WebhookNotifier notifier(String url) {
		return new WebhookNotifier(HttpClient.newHttpClient(), JsonMapper.builder().build(),
				new AlertProperties(Duration.ofMinutes(15), "f@x", url));
	}

	private String hookUrl() {
		return "http://localhost:" + server.getAddress().getPort() + "/hook";
	}

	@Test
	void postsTheAlertAsJson() {
		notifier(hookUrl()).send(message);

		assertThat(receivedContentType.get()).isEqualTo("application/json");
		assertThat(receivedBody.get()).isEqualTo(
				"{\"type\":\"INCIDENT_OPENED\",\"monitorId\":1,\"incidentId\":2,"
						+ "\"subject\":\"[PulseWatch] DOWN: Homepage\",\"message\":\"Line 1\\n\\\"quoted\\\"\"}");
	}

	@Test
	void aNon2xxAnswerIsAFailure() {
		answerWith = 500;

		assertThatThrownBy(() -> notifier(hookUrl()).send(message))
				.isInstanceOf(NotificationException.class)
				.hasMessageContaining("500");
	}

	@Test
	void anUnreachableServerIsAFailure() {
		server.stop(0);

		assertThatThrownBy(() -> notifier(hookUrl()).send(message)).isInstanceOf(NotificationException.class);
	}

	@Test
	void isEnabledOnlyWhenAUrlIsConfigured() {
		assertThat(notifier(hookUrl()).isEnabled()).isTrue();
		assertThat(notifier(null).isEnabled()).isFalse();
		assertThat(notifier("   ").isEnabled()).isFalse();
	}
}
