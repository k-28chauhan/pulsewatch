package com.karaninder.pulseWatch.stats;

import com.karaninder.pulseWatch.auth.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/monitors")
public class StatsController {

	private final StatsService service;

	public StatsController(StatsService service) {
		this.service = service;
	}

	/** The monitor list with each monitor's current status. A literal path, so it wins over /{id}. */
	@GetMapping("/overview")
	public List<MonitorOverview> overview(@AuthenticationPrincipal Jwt jwt) {
		return service.overview(AuthenticatedUser.id(jwt));
	}

	/** Everything a monitor's detail page needs in one call. */
	@GetMapping("/{id}/stats")
	public StatsResponse stats(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return service.stats(AuthenticatedUser.id(jwt), id);
	}

	/** Chart data: failed vs total checks per time bucket. window = 24h | 7d | 30d. */
	@GetMapping("/{id}/error-rate")
	public List<ErrorRateBucket> errorRate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
	                                      @RequestParam(defaultValue = "24h") String window) {
		return service.errorRate(AuthenticatedUser.id(jwt), id, StatsWindow.fromLabel(window));
	}

	/** Chart data: p50 and p95 latency per time bucket. window = 24h | 7d. */
	@GetMapping("/{id}/latency")
	public List<LatencyBucket> latency(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
	                                  @RequestParam(defaultValue = "24h") String window) {
		return service.latencySeries(AuthenticatedUser.id(jwt), id, StatsWindow.fromLabel(window));
	}
}
