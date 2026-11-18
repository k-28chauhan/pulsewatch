package com.karaninder.pulseWatch.alert;

import com.karaninder.pulseWatch.auth.User;
import com.karaninder.pulseWatch.auth.UserRepository;
import com.karaninder.pulseWatch.check.SlowResponseDetected;
import com.karaninder.pulseWatch.incident.IncidentOpened;
import com.karaninder.pulseWatch.incident.IncidentResolved;
import com.karaninder.pulseWatch.monitor.Monitor;
import com.karaninder.pulseWatch.monitor.MonitorMethod;
import com.karaninder.pulseWatch.monitor.MonitorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

	@Mock
	AlertEventRepository auditLog;

	@Mock
	MonitorRepository monitors;

	@Mock
	UserRepository users;

	private final MeterRegistry meters = new SimpleMeterRegistry();

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC); // time stands still in this test
	private final AlertProperties properties = new AlertProperties(Duration.ofMinutes(15), "from@x", null);

	/** A hand-written fake: records what it was asked to send, and can be told to fail. */
	private static class FakeNotifier implements Notifier {
		final AlertChannel channel;
		final boolean enabled;
		final boolean failing;
		final List<AlertMessage> sent = new ArrayList<>();

		FakeNotifier(AlertChannel channel, boolean enabled, boolean failing) {
			this.channel = channel;
			this.enabled = enabled;
			this.failing = failing;
		}

		@Override
		public AlertChannel channel() {
			return channel;
		}

		@Override
		public boolean isEnabled() {
			return enabled;
		}

		@Override
		public void send(AlertMessage message) {
			if (failing) {
				throw new NotificationException("connection refused");
			}
			sent.add(message);
		}
	}

	private final FakeNotifier email = new FakeNotifier(AlertChannel.EMAIL, true, false);
	private final FakeNotifier webhook = new FakeNotifier(AlertChannel.WEBHOOK, true, false);

	private AlertService service(Notifier... notifiers) {
		return new AlertService(List.of(notifiers), auditLog, monitors, users, properties, clock, meters);
	}

	@BeforeEach
	void monitorExists() {
		Monitor monitor = new Monitor(3L, "Homepage", "https://example.com", MonitorMethod.GET, 200, 60, 5000, 800, 3);
		ReflectionTestUtils.setField(monitor, "id", 5L);
		org.mockito.Mockito.lenient().when(monitors.findById(5L)).thenReturn(Optional.of(monitor));
		org.mockito.Mockito.lenient().when(users.findById(3L)).thenReturn(Optional.of(new User("owner@x.test", "hash")));
	}

	// ---- down / recovered ----

	@Test
	void anOpenedIncidentSendsADownAlertOnEveryEnabledChannelAndAuditsEach() {
		service(email, webhook).onIncidentOpened(new IncidentOpened(9L, 5L, NOW.minusSeconds(120), "Timed out"));

		assertThat(email.sent).hasSize(1);
		AlertMessage message = email.sent.get(0);
		assertThat(message.type()).isEqualTo(AlertType.INCIDENT_OPENED);
		assertThat(message.subject()).isEqualTo("[PulseWatch] DOWN: Homepage");
		assertThat(message.body()).contains("https://example.com", "Timed out");
		assertThat(message.incidentId()).isEqualTo(9L);
		assertThat(message.recipientEmail()).isEqualTo("owner@x.test"); // the alert goes to the monitor's owner
		assertThat(webhook.sent).hasSize(1);

		ArgumentCaptor<AlertEvent> rows = ArgumentCaptor.forClass(AlertEvent.class);
		verify(auditLog, org.mockito.Mockito.times(2)).save(rows.capture());
		assertThat(rows.getAllValues()).extracting(AlertEvent::getChannel)
				.containsExactly(AlertChannel.EMAIL, AlertChannel.WEBHOOK);
		assertThat(rows.getAllValues()).allSatisfy(row -> {
			assertThat(row.getStatus()).isEqualTo(AlertStatus.SENT);
			assertThat(row.getType()).isEqualTo(AlertType.INCIDENT_OPENED);
			assertThat(row.getMonitorId()).isEqualTo(5L);
			assertThat(row.getIncidentId()).isEqualTo(9L);
			assertThat(row.getCreatedAt()).isEqualTo(NOW);
		});
	}

	@Test
	void aResolvedIncidentSendsARecoveredAlertWithTheOutageDuration() {
		Instant started = NOW.minusSeconds(252);

		service(email).onIncidentResolved(new IncidentResolved(9L, 5L, started, NOW));

		AlertMessage message = email.sent.get(0);
		assertThat(message.type()).isEqualTo(AlertType.INCIDENT_RESOLVED);
		assertThat(message.subject()).isEqualTo("[PulseWatch] RECOVERED: Homepage");
		assertThat(message.body()).contains("Outage lasted: 4m 12s");
	}

	@Test
	void aDisabledChannelIsSkippedAndNotAudited() {
		FakeNotifier disabledWebhook = new FakeNotifier(AlertChannel.WEBHOOK, false, false);

		service(email, disabledWebhook).onIncidentOpened(new IncidentOpened(9L, 5L, NOW, "boom"));

		assertThat(disabledWebhook.sent).isEmpty();
		ArgumentCaptor<AlertEvent> rows = ArgumentCaptor.forClass(AlertEvent.class);
		verify(auditLog).save(rows.capture()); // only the email row
		assertThat(rows.getValue().getChannel()).isEqualTo(AlertChannel.EMAIL);
	}

	@Test
	void aFailingChannelIsAuditedAsFailedAndDoesNotStopTheOthers() {
		FakeNotifier brokenEmail = new FakeNotifier(AlertChannel.EMAIL, true, true);

		service(brokenEmail, webhook).onIncidentOpened(new IncidentOpened(9L, 5L, NOW, "boom"));

		assertThat(webhook.sent).hasSize(1); // still delivered
		ArgumentCaptor<AlertEvent> rows = ArgumentCaptor.forClass(AlertEvent.class);
		verify(auditLog, org.mockito.Mockito.times(2)).save(rows.capture());
		AlertEvent failed = rows.getAllValues().get(0);
		assertThat(failed.getChannel()).isEqualTo(AlertChannel.EMAIL);
		assertThat(failed.getStatus()).isEqualTo(AlertStatus.FAILED);
		assertThat(failed.getError()).isEqualTo("connection refused");
		assertThat(rows.getAllValues().get(1).getStatus()).isEqualTo(AlertStatus.SENT);
	}

	@Test
	void aDeletedMonitorMeansNoAlert() {
		when(monitors.findById(6L)).thenReturn(Optional.empty());

		service(email).onIncidentOpened(new IncidentOpened(9L, 6L, NOW, "boom"));

		assertThat(email.sent).isEmpty();
		verify(auditLog, never()).save(any());
	}

	// ---- latency alerts and the cooldown ----

	@Test
	void aSlowResponseSendsALatencyAlertWhenNoneWasSentRecently() {
		when(auditLog.existsByMonitorIdAndTypeAndStatusAndCreatedAtAfter(
				5L, AlertType.LATENCY_EXCEEDED, AlertStatus.SENT, NOW.minus(Duration.ofMinutes(15)))).thenReturn(false);

		service(email).onSlowResponse(new SlowResponseDetected(5L, 1200, 800, NOW));

		AlertMessage message = email.sent.get(0);
		assertThat(message.type()).isEqualTo(AlertType.LATENCY_EXCEEDED);
		assertThat(message.subject()).isEqualTo("[PulseWatch] SLOW: Homepage");
		assertThat(message.body()).contains("1200 ms", "threshold 800 ms");
		assertThat(message.incidentId()).isNull();
	}

	@Test
	void aSlowResponseInsideTheCooldownIsSuppressed() {
		when(auditLog.existsByMonitorIdAndTypeAndStatusAndCreatedAtAfter(
				5L, AlertType.LATENCY_EXCEEDED, AlertStatus.SENT, NOW.minus(Duration.ofMinutes(15)))).thenReturn(true);

		service(email).onSlowResponse(new SlowResponseDetected(5L, 1200, 800, NOW));

		assertThat(email.sent).isEmpty();
		verify(auditLog, never()).save(any());
	}

	// ---- formatting ----

	@Test
	void durationsAreWrittenForHumans() {
		assertThat(AlertService.describe(Duration.ofSeconds(45))).isEqualTo("45s");
		assertThat(AlertService.describe(Duration.ofSeconds(252))).isEqualTo("4m 12s");
		assertThat(AlertService.describe(Duration.ofSeconds(3725))).isEqualTo("1h 2m 5s");
	}

	// ---- metrics ----

	@Test
	void everyDeliveryAttemptIsCountedByTypeChannelAndStatus() {
		FakeNotifier brokenWebhook = new FakeNotifier(AlertChannel.WEBHOOK, true, true);

		service(email, brokenWebhook).onIncidentOpened(new IncidentOpened(9L, 5L, NOW, "boom"));

		assertThat(meters.counter("pulsewatch.alerts", "type", "INCIDENT_OPENED", "channel", "EMAIL", "status", "SENT").count()).isEqualTo(1.0);
		assertThat(meters.counter("pulsewatch.alerts", "type", "INCIDENT_OPENED", "channel", "WEBHOOK", "status", "FAILED").count()).isEqualTo(1.0);
	}
}
