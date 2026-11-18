package com.karaninder.pulseWatch.monitor;

import com.karaninder.pulseWatch.check.CheckProperties;
import com.karaninder.pulseWatch.check.TargetGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pure unit test: no Spring, no database. The repository is a Mockito fake. */
@ExtendWith(MockitoExtension.class)
class MonitorServiceTest {

	private static final Long ME = 7L;
	private static final Long SOMEONE_ELSE = 8L;

	@Mock
	MonitorRepository repository;

	private MonitorService service() {
		return new MonitorService(repository, new TargetGuard(new CheckProperties(false))); // the real, strict guard
	}

	private MonitorRequest request(String name) {
		return new MonitorRequest(name, "https://example.com", null, null, null, null, null, null);
	}

	private Monitor existing() {
		return new Monitor(ME, "old", "https://old.example.com", MonitorMethod.GET, 200, 60, 5000, null, 3);
	}

	@Test
	void createSavesAnEnabledMonitorOwnedByTheCallerAndDueImmediately() {
		when(repository.save(any(Monitor.class))).thenAnswer(inv -> inv.getArgument(0));

		Monitor created = service().create(ME, request("Homepage"));

		ArgumentCaptor<Monitor> saved = ArgumentCaptor.forClass(Monitor.class);
		verify(repository).save(saved.capture());
		assertThat(saved.getValue().getName()).isEqualTo("Homepage");
		assertThat(saved.getValue().getUserId()).isEqualTo(ME);
		assertThat(created.isEnabled()).isTrue();
		assertThat(created.getNextCheckAt()).isNotNull();
	}

	@Test
	void requestDefaultsAreAppliedWhenFieldsAreMissing() {
		MonitorRequest r = request("Homepage");

		assertThat(r.method()).isEqualTo(MonitorMethod.GET);
		assertThat(r.expectedStatus()).isEqualTo(200);
		assertThat(r.intervalSeconds()).isEqualTo(60);
		assertThat(r.timeoutMs()).isEqualTo(5000);
		assertThat(r.failureThreshold()).isEqualTo(3);
		assertThat(r.latencyThresholdMs()).isNull();
	}

	@Test
	void findAllAsksOnlyForTheCallersMonitors() {
		Monitor mine = existing();
		when(repository.findByUserIdOrderById(ME)).thenReturn(List.of(mine));

		assertThat(service().findAll(ME)).containsExactly(mine);
		verify(repository, never()).findAll();
	}

	@Test
	void findByIdThrowsWhenMissing() {
		when(repository.findByIdAndUserId(99L, ME)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().findById(ME, 99L))
				.isInstanceOf(MonitorNotFoundException.class)
				.hasMessageContaining("99");
	}

	@Test
	void someoneElsesMonitorLooksExactlyLikeAMissingOne() {
		// Monitor 1 exists, but belongs to ME. The query for SOMEONE_ELSE finds nothing.
		when(repository.findByIdAndUserId(1L, SOMEONE_ELSE)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().findById(SOMEONE_ELSE, 1L)).isInstanceOf(MonitorNotFoundException.class);
		assertThatThrownBy(() -> service().update(SOMEONE_ELSE, 1L, request("hijack"))).isInstanceOf(MonitorNotFoundException.class);
		assertThatThrownBy(() -> service().pause(SOMEONE_ELSE, 1L)).isInstanceOf(MonitorNotFoundException.class);
		assertThatThrownBy(() -> service().resume(SOMEONE_ELSE, 1L)).isInstanceOf(MonitorNotFoundException.class);
		assertThatThrownBy(() -> service().delete(SOMEONE_ELSE, 1L)).isInstanceOf(MonitorNotFoundException.class);
		verify(repository, never()).delete(any());
	}

	@Test
	void updateReplacesTheFields() {
		when(repository.findByIdAndUserId(1L, ME)).thenReturn(Optional.of(existing()));

		Monitor updated = service().update(ME, 1L, request("new name"));

		assertThat(updated.getName()).isEqualTo("new name");
		assertThat(updated.getUrl()).isEqualTo("https://example.com");
		assertThat(updated.getUserId()).isEqualTo(ME); // ownership never changes
	}

	@Test
	void pauseDisablesAndResumeEnablesAgain() {
		Monitor monitor = existing();
		when(repository.findByIdAndUserId(1L, ME)).thenReturn(Optional.of(monitor));

		assertThat(service().pause(ME, 1L).isEnabled()).isFalse();
		assertThat(service().resume(ME, 1L).isEnabled()).isTrue();
	}

	@Test
	void deleteOfUnknownIdThrowsAndDeletesNothing() {
		when(repository.findByIdAndUserId(5L, ME)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().delete(ME, 5L)).isInstanceOf(MonitorNotFoundException.class);
		verify(repository, never()).delete(any());
	}

	@Test
	void deleteRemovesAnExistingMonitor() {
		Monitor monitor = existing();
		when(repository.findByIdAndUserId(5L, ME)).thenReturn(Optional.of(monitor));

		service().delete(ME, 5L);

		verify(repository).delete(monitor);
	}

	// ---- SSRF protection ----

	private MonitorRequest requestFor(String url) {
		return new MonitorRequest("x", url, null, null, null, null, null, null);
	}

	@Test
	void monitorsPointingAtInternalAddressesAreRefusedOnCreateAndNothingIsSaved() {
		for (String url : new String[]{"http://127.0.0.1/", "http://localhost:5432/", "http://169.254.169.254/latest/meta-data/",
				"http://10.0.0.5/admin", "http://192.168.1.1/", "http://[::1]/", "http://172.16.0.9:8080/"}) {
			assertThatThrownBy(() -> service().create(ME, requestFor(url)))
					.as(url).isInstanceOf(ForbiddenTargetException.class)
					.hasMessageContaining("private or internal");
		}
		verify(repository, never()).save(any());
	}

	@Test
	void updatingAMonitorToAnInternalAddressIsRefusedToo() {
		assertThatThrownBy(() -> service().update(ME, 1L, requestFor("http://169.254.169.254/")))
				.isInstanceOf(ForbiddenTargetException.class);
		verify(repository, never()).findByIdAndUserId(any(), any());
	}

	@Test
	void aPublicAddressIsAccepted() {
		when(repository.save(any(Monitor.class))).thenAnswer(inv -> inv.getArgument(0));

		Monitor created = service().create(ME, requestFor("http://93.184.216.34/health")); // a public IP literal: no DNS needed

		assertThat(created.getUrl()).isEqualTo("http://93.184.216.34/health");
	}
}
