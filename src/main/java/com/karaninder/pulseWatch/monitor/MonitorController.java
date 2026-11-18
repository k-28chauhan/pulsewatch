package com.karaninder.pulseWatch.monitor;

import com.karaninder.pulseWatch.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * The HTTP layer: translates requests into service calls and results into JSON.
 * No business logic here.
 */
@RestController // = @Controller + @ResponseBody: return values are written as JSON
@RequestMapping("/api/monitors")
public class MonitorController {

	private final MonitorService service;

	public MonitorController(MonitorService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<MonitorResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MonitorRequest request) {
		Monitor created = service.create(AuthenticatedUser.id(jwt), request);
		// REST convention: 201 Created + a Location header pointing at the new resource.
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(created.getId()).toUri();
		return ResponseEntity.created(location).body(MonitorResponse.from(created));
	}

	@GetMapping
	public List<MonitorResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return service.findAll(AuthenticatedUser.id(jwt)).stream().map(MonitorResponse::from).toList();
	}

	@GetMapping("/{id}")
	public MonitorResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return MonitorResponse.from(service.findById(AuthenticatedUser.id(jwt), id));
	}

	@PutMapping("/{id}")
	public MonitorResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody MonitorRequest request) {
		return MonitorResponse.from(service.update(AuthenticatedUser.id(jwt), id, request));
	}

	@PostMapping("/{id}/pause")
	public MonitorResponse pause(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return MonitorResponse.from(service.pause(AuthenticatedUser.id(jwt), id));
	}

	@PostMapping("/{id}/resume")
	public MonitorResponse resume(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return MonitorResponse.from(service.resume(AuthenticatedUser.id(jwt), id));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		service.delete(AuthenticatedUser.id(jwt), id);
		return ResponseEntity.noContent().build(); // 204
	}
}
