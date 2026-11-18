package com.karaninder.pulseWatch.alert;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends a plain-text email through the SMTP server configured in spring.mail.* (Mailpit locally). */
@Component
public class EmailNotifier implements Notifier {

	private final JavaMailSender mailSender;
	private final AlertProperties properties;

	public EmailNotifier(JavaMailSender mailSender, AlertProperties properties) {
		this.mailSender = mailSender;
		this.properties = properties;
	}

	@Override
	public AlertChannel channel() {
		return AlertChannel.EMAIL;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public void send(AlertMessage message) {
		SimpleMailMessage mail = new SimpleMailMessage();
		mail.setFrom(properties.from());
		mail.setTo(message.recipientEmail());
		mail.setSubject(message.subject());
		mail.setText(message.body());
		try {
			mailSender.send(mail);
		} catch (MailException e) {
			throw new NotificationException("Email delivery failed: " + e.getMessage(), e);
		}
	}
}
