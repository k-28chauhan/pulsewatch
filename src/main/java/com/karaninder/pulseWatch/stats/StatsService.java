package com.karaninder.pulseWatch.stats;

import com.karaninder.pulseWatch.incident.IncidentRepository;
import com.karaninder.pulseWatch.incident.IncidentResponse;
import com.karaninder.pulseWatch.monitor.MonitorNotFoundException;
import com.karaninder.pulseWatch.monitor.MonitorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Builds the numbers behind a monitor's detail page. Read-only: nothing here changes data. */
@Service
@Transactional(readOnly = true)
public class StatsService {

	private final MonitorRepository monitors;
	private final IncidentRepository incidents;
	private final StatsRepository stats;
	private final StatsProperties properties;
	private final Clock clock;

	public StatsService(MonitorRepository monitors, IncidentRepository incidents, StatsRepository stats,
	                    StatsProperties properties, Clock clock) {
		this.monitors = monitors;
		this.incidents = incidents;
		this.stats = stats;
		this.properties = properties;
		this.clock = clock;
	}

	public StatsResponse stats(long userId, long monitorId) {
		requireOwnedMonitor(userId, monitorId);
		Instant now = clock.instant();
		Instant boundary = boundary(now);

		return new StatsResponse(
				monitorId,
				now,
				uptime(monitorId, StatsWindow.LAST_24H, now, boundary),
				uptime(monitorId, StatsWindow.LAST_7D, now, boundary),
				uptime(monitorId, StatsWindow.LAST_30D, now, boundary),
				stats.latency(monitorId, now.minus(StatsWindow.LAST_24H.length())),
				stats.latency(monitorId, now.minus(StatsWindow.LAST_7D.length())),
				buckets(monitorId, StatsWindow.LAST_24H, now, boundary),
				incidents.findByMonitorIdAndResolvedAtIsNull(monitorId)
						.map(incident -> IncidentResponse.from(incident, now))
						.orElse(null));
	}

	/** The monitor list: every monitor of the user with its current status, in one database round trip. */
	public List<MonitorOverview> overview(long userId) {
		Instant now = clock.instant();
		return stats.overview(userId, now.minus(StatsWindow.LAST_24H.length())).stream()
				.map(row -> new MonitorOverview(
						row.id(), row.name(), row.url(),
						statusOf(row), row.enabled(), row.intervalSeconds(),
						row.lastCheckedAt(),
						Boolean.TRUE.equals(row.lastSuccess()) ? row.lastLatencyMs() : null,
						toUptime(new WindowCounts(row.totalChecks(), row.failedChecks(), 0)).uptimePercent()))
				.toList();
	}

	static MonitorStatus statusOf(OverviewRow row) {
		if (!row.enabled()) {
			return MonitorStatus.PAUSED;
		}
		if (row.hasOpenIncident()) {
			return MonitorStatus.DOWN;
		}
		if (row.lastCheckedAt() == null) {
			return MonitorStatus.PENDING;
		}
		return Boolean.TRUE.equals(row.lastSuccess()) ? MonitorStatus.UP : MonitorStatus.DEGRADED;
	}

	/** Latency percentiles per time bucket for a chart. Only 24h and 7d: they come from raw rows. */
	public List<LatencyBucket> latencySeries(long userId, long monitorId, StatsWindow window) {
		requireOwnedMonitor(userId, monitorId);
		if (window.latencyBucket() == null) {
			throw InvalidStatsWindowException.noLatencySeries(window);
		}
		return stats.latencyBuckets(monitorId, clock.instant().minus(window.length()), window.latencyBucket().toSeconds());
	}

	public List<ErrorRateBucket> errorRate(long userId, long monitorId, StatsWindow window) {
		requireOwnedMonitor(userId, monitorId);
		Instant now = clock.instant();
		return buckets(monitorId, window, now, boundary(now));
	}

	/**
	 * Where raw rows end and hourly rollups begin: the start of the hour that is rawRetention ago.
	 * It must be computed exactly like the nightly job's cutoff (StatsMaintenanceService).
	 */
	private Instant boundary(Instant now) {
		return now.minus(properties.rawRetention()).truncatedTo(ChronoUnit.HOURS);
	}

	private UptimeStats uptime(long monitorId, StatsWindow window, Instant now, Instant boundary) {
		WindowCounts counts = stats.counts(monitorId, now.minus(window.length()), boundary);
		return toUptime(counts);
	}

	private List<ErrorRateBucket> buckets(long monitorId, StatsWindow window, Instant now, Instant boundary) {
		return stats.errorRateBuckets(monitorId, now.minus(window.length()), boundary, window.bucket().toSeconds());
	}

	static UptimeStats toUptime(WindowCounts counts) {
		if (counts.totalChecks() == 0) {
			return new UptimeStats(null, 0, 0, null); // "no data" is not "100% up" and not "0% up"
		}
		double uptime = round(counts.successfulChecks() * 100.0 / counts.totalChecks(), 3);
		Double avgLatency = counts.successfulChecks() == 0
				? null
				: round((double) counts.latencySumMs() / counts.successfulChecks(), 1);
		return new UptimeStats(uptime, counts.totalChecks(), counts.failedChecks(), avgLatency);
	}

	private static double round(double value, int decimals) {
		double factor = Math.pow(10, decimals);
		return Math.round(value * factor) / factor;
	}

	/** Someone else's monitor is "not found", exactly like one that does not exist. */
	private void requireOwnedMonitor(long userId, long monitorId) {
		if (!monitors.existsByIdAndUserId(monitorId, userId)) {
			throw new MonitorNotFoundException(monitorId);
		}
	}
}
