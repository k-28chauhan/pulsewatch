package com.karaninder.pulseWatch.alert;

/**
 * The channel-independent content of an alert. Each Notifier decides how to present it
 * (an email uses subject + body, the webhook sends all fields as JSON).
 *
 * @param incidentId     null for latency alerts, which do not belong to an incident
 * @param recipientEmail the email of the monitor's owner
 */
public record AlertMessage(AlertType type, Long monitorId, Long incidentId, String recipientEmail, String subject, String body) {
}
