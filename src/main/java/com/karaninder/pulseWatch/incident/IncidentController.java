package com.karaninder.pulseWatch.incident;

import com.karaninder.pulseWatch.auth.AuthenticatedUser;
import com.karaninder.pulseWatch.common.PageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitors/{monitorId}/incidents")
public class IncidentController {

	private final IncidentQueryService service;

	public IncidentController(IncidentQueryService service) {
		this.service = service;
	}

	/** GET /api/monitors/5/incidents?page=0&size=20, newest incident first. */
	@GetMapping
	public PageResponse<IncidentResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable Long monitorId,
	                                              @RequestParam(defaultValue = "0") int page,
	                                              @RequestParam(defaultValue = "20") int size) {
		return service.history(AuthenticatedUser.id(jwt), monitorId, page, size);
	}
}
