package com.karaninder.pulseWatch.alert;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {

	/** Cooldown check: was an alert of this type successfully sent for this monitor after the given time? */
	boolean existsByMonitorIdAndTypeAndStatusAndCreatedAtAfter(Long monitorId, AlertType type, AlertStatus status, Instant after);
}
