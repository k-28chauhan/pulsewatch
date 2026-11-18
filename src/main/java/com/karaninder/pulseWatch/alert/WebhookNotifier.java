package com.karaninder.pulseWatch.alert;

import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** POSTs the alert as JSON to pulsewatch.alerts.webhook-url. Any non-2xx answer counts as a failure. */
@Component
public class WebhookNotifier implements Notifier {

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private final HttpClient client;
	private final JsonMapper jsonMapper;
	private final AlertProperties properties;

	public WebhookNotifier(HttpClient client, JsonMapper jsonMapper, AlertProperties properties) {
		this.client = client;
		this.jsonMapper = jsonMapper;
		this.properties = properties;
	}

	@Override
	public AlertChannel channel() {
		return AlertChannel.WEBHOOK;
	}

	@Override
	public boolean isEnabled() {
		return properties.webhookUrl() != null && !properties.webhookUrl().isBlank();
	}

	@Override
	public void send(AlertMessage message) {
		Map<String, Object> payload = new LinkedHashMap<>(); // keeps the field order stable
		payload.put("type", message.type().name());
		payload.put("monitorId", message.monitorId());
		payload.put("incidentId", message.incidentId());
		payload.put("subject", message.subject());
		payload.put("message", message.body());

		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(properties.webhookUrl()))
					.timeout(TIMEOUT)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(payload)))
					.build();
			HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
			if (response.statusCode() / 100 != 2) {
				throw new NotificationException("Webhook answered with status " + response.statusCode());
			}
		} catch (IOException | IllegalArgumentException e) {
			throw new NotificationException("Webhook delivery failed: " + e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new NotificationException("Webhook delivery was interrupted", e);
		}
	}
}
