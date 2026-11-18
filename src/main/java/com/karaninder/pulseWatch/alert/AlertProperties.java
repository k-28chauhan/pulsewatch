package com.karaninder.pulseWatch.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Typed view of the pulsewatch.alerts.* block in application.yaml. Spring binds the YAML to this record
 * at startup; a value of the wrong type (cooldown: banana) stops the app with a clear error.
 *
 * @param from       the sender address of alert emails (recipients are the monitors' owners)
 * @param cooldown   minimum time between two latency alerts for the same monitor
 * @param webhookUrl empty or null disables the webhook channel
 */
@ConfigurationProperties(prefix = "pulsewatch.alerts")
public record AlertProperties(
		@DefaultValue("PT15M") Duration cooldown,
		@DefaultValue("pulsewatch@localhost") String from,
		String webhookUrl
) {
}
