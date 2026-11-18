package com.karaninder.pulseWatch.alert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailNotifierTest {

	@Mock
	JavaMailSender mailSender;

	private final AlertProperties properties = new AlertProperties(Duration.ofMinutes(15), "pulse@x.test", null);
	private final AlertMessage message = new AlertMessage(AlertType.INCIDENT_OPENED, 1L, 2L, "owner@x.test", "[PulseWatch] DOWN: Homepage", "It is down");

	@Test
	void sendsAPlainTextMailFromTheConfiguredSenderToTheMonitorOwner() {
		new EmailNotifier(mailSender, properties).send(message);

		ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(mail.capture());
		assertThat(mail.getValue().getFrom()).isEqualTo("pulse@x.test");
		assertThat(mail.getValue().getTo()).containsExactly("owner@x.test");
		assertThat(mail.getValue().getSubject()).isEqualTo("[PulseWatch] DOWN: Homepage");
		assertThat(mail.getValue().getText()).isEqualTo("It is down");
	}

	@Test
	void aMailServerErrorBecomesANotificationException() {
		doThrow(new MailSendException("Connection refused")).when(mailSender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));

		assertThatThrownBy(() -> new EmailNotifier(mailSender, properties).send(message))
				.isInstanceOf(NotificationException.class)
				.hasMessageContaining("Connection refused");
	}

	@Test
	void isAlwaysEnabledAndReportsItsChannel() {
		EmailNotifier notifier = new EmailNotifier(mailSender, properties);

		assertThat(notifier.isEnabled()).isTrue();
		assertThat(notifier.channel()).isEqualTo(AlertChannel.EMAIL);
	}
}
