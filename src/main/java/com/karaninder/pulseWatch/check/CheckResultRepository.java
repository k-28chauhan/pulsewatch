package com.karaninder.pulseWatch.check;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {

	/**
	 * Newest results of one monitor first. The Pageable is only used as "LIMIT n":
	 * PageRequest.of(0, 3) returns the 3 newest rows. Served by idx_check_results_monitor_time.
	 */
	List<CheckResult> findByMonitorIdOrderByCheckedAtDesc(Long monitorId, Pageable limit);
}
