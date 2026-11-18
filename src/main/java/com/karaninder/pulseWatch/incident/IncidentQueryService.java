package com.karaninder.pulseWatch.incident;

import com.karaninder.pulseWatch.common.PageResponse;
import com.karaninder.pulseWatch.monitor.MonitorNotFoundException;
import com.karaninder.pulseWatch.monitor.MonitorRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Read side of incidents: paginated history, newest first. */
@Service
@Transactional(readOnly = true)
public class IncidentQueryService {

	static final int MAX_PAGE_SIZE = 100;

	private final IncidentRepository incidents;
	private final MonitorRepository monitors;
	private final Clock clock;

	public IncidentQueryService(IncidentRepository incidents, MonitorRepository monitors, Clock clock) {
		this.incidents = incidents;
		this.monitors = monitors;
		this.clock = clock;
	}

	/** Out-of-range input is clamped instead of rejected: page at least 0, size between 1 and 100. */
	public PageResponse<IncidentResponse> history(long userId, long monitorId, int page, int size) {
		if (!monitors.existsByIdAndUserId(monitorId, userId)) {
			throw new MonitorNotFoundException(monitorId);
		}
		PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
				Sort.by(Sort.Direction.DESC, "startedAt"));
		var now = clock.instant();
		return PageResponse.of(incidents.findByMonitorId(monitorId, request), incident -> IncidentResponse.from(incident, now));
	}
}
