package com.karaninder.pulseWatch.monitor;

import com.karaninder.pulseWatch.check.TargetGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic layer. The controller handles HTTP, the repository handles SQL,
 * and this class sits between them and decides what the operations mean.
 * <p>
 * Every method takes the id of the logged-in user and only ever touches that user's monitors.
 * Someone else's monitor is reported as "not found" (404), never "forbidden": that would confirm it exists.
 */
@Service
@Transactional // every public method runs in one DB transaction: all-or-nothing
public class MonitorService {

	private final MonitorRepository repository;
	private final TargetGuard targetGuard;

	// Constructor injection: Spring sees the one constructor and passes in the beans.
	// "final" + constructor means a MonitorService can never exist without its collaborators.
	public MonitorService(MonitorRepository repository, TargetGuard targetGuard) {
		this.repository = repository;
		this.targetGuard = targetGuard;
	}

	/** SSRF protection: refuse URLs that point at the server's own network. */
	private void requirePublicTarget(String url) {
		targetGuard.violation(url).ifPresent(reason -> {
			throw new ForbiddenTargetException(reason);
		});
	}

	public Monitor create(Long userId, MonitorRequest request) {
		requirePublicTarget(request.url());
		Monitor monitor = new Monitor(
				userId, request.name(), request.url(), request.method(), request.expectedStatus(),
				request.intervalSeconds(), request.timeoutMs(), request.latencyThresholdMs(),
				request.failureThreshold());
		return repository.save(monitor);
	}

	@Transactional(readOnly = true) // hint: no writes here, lets Hibernate skip dirty-checking
	public List<Monitor> findAll(Long userId) {
		return repository.findByUserIdOrderById(userId);
	}

	@Transactional(readOnly = true)
	public Monitor findById(Long userId, Long id) {
		return repository.findByIdAndUserId(id, userId).orElseThrow(() -> new MonitorNotFoundException(id));
	}

	public Monitor update(Long userId, Long id, MonitorRequest request) {
		requirePublicTarget(request.url());
		Monitor monitor = findById(userId, id);
		monitor.applyChanges(
				request.name(), request.url(), request.method(), request.expectedStatus(),
				request.intervalSeconds(), request.timeoutMs(), request.latencyThresholdMs(),
				request.failureThreshold());
		// No save() call needed: the monitor is "managed" inside this transaction, so
		// Hibernate notices the changed fields and issues the UPDATE when we commit.
		return monitor;
	}

	public Monitor pause(Long userId, Long id) {
		Monitor monitor = findById(userId, id);
		monitor.pause();
		return monitor;
	}

	public Monitor resume(Long userId, Long id) {
		Monitor monitor = findById(userId, id);
		monitor.resume();
		return monitor;
	}

	public void delete(Long userId, Long id) {
		repository.delete(findById(userId, id));
	}
}
