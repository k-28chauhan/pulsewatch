package com.karaninder.pulseWatch.incident;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

	/** The open incident of a monitor, if any. The partial unique index guarantees there is at most one. */
	Optional<Incident> findByMonitorIdAndResolvedAtIsNull(Long monitorId);

	/** One page of a monitor's incident history. The caller decides the sort order through the Pageable. */
	Page<Incident> findByMonitorId(Long monitorId, Pageable pageable);

	/** How many monitors are down right now (served by the partial unique index). */
	long countByResolvedAtIsNull();
}
