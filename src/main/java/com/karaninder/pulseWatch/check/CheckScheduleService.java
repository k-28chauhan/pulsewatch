package com.karaninder.pulseWatch.check;

import com.karaninder.pulseWatch.monitor.Monitor;
import com.karaninder.pulseWatch.monitor.MonitorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Answers "which monitors should be checked right now?" and CLAIMS them.
 * <p>
 * Claiming = moving next_check_at one interval ahead, inside one short transaction, BEFORE any
 * network call happens. Without it, a slow check (up to its timeout) would still be "due" on the
 * next tick 10 seconds later and get checked twice.
 */
@Service
public class CheckScheduleService {

	private final MonitorRepository monitors;

	public CheckScheduleService(MonitorRepository monitors) {
		this.monitors = monitors;
	}

	@Transactional
	public List<CheckTarget> claimDueMonitors(Instant now) {
		List<Monitor> due = monitors.findByEnabledTrueAndNextCheckAtLessThanEqual(now);
		due.forEach(monitor -> monitor.scheduleNextCheck(now)); // dirty checking saves this on commit
		return due.stream().map(CheckTarget::from).toList();
	}
}
