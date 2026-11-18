package com.karaninder.pulseWatch.monitor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * No implementation needed: Spring Data generates one at startup.
 * Every method that serves a USER request takes the owner's id, so one user can never reach another's monitor.
 */
public interface MonitorRepository extends JpaRepository<Monitor, Long> {

	List<Monitor> findByUserIdOrderById(Long userId);

	Optional<Monitor> findByIdAndUserId(Long id, Long userId);

	boolean existsByIdAndUserId(Long id, Long userId);

	/**
	 * A "derived query": Spring Data reads the method NAME and writes the SQL:
	 * SELECT * FROM monitors WHERE enabled = true AND next_check_at <= ?
	 * (the partial index idx_monitors_due from V1 serves exactly this query).
	 * Deliberately NOT filtered by user: the scheduler works for the whole system.
	 */
	List<Monitor> findByEnabledTrueAndNextCheckAtLessThanEqual(Instant now);
}
