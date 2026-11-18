package com.karaninder.pulseWatch.common;

import com.karaninder.pulseWatch.TestcontainersConfig;
import com.karaninder.pulseWatch.check.CheckScheduleService;
import com.karaninder.pulseWatch.check.CheckScheduler;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "Two app copies don't double-check", proven on a real database. Each JdbcTemplateLockProvider below plays one
 * app instance: they share only the shedlock table, exactly like two JVMs would.
 */
@SpringBootTest(properties = {"pulsewatch.check.tick-delay=PT1H"}) // the scheduler is ON, but its automatic tick only fires once at startup
@Import(TestcontainersConfig.class)
class SchedulerLockTest {

	private static final String LOCK = "checkScheduler.tick";

	@Autowired
	DataSource dataSource;

	@Autowired
	LockProvider lockProvider; // the application's own provider

	@Autowired
	CheckScheduler scheduler; // a proxy that takes the lock around tick()

	@MockitoBean
	CheckScheduleService scheduleService;

	private LockProvider anotherInstance() {
		return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
				.withJdbcTemplate(new JdbcTemplate(dataSource)).usingDbTime().build());
	}

	private LockConfiguration config(String name) {
		return new LockConfiguration(Instant.now(), name, Duration.ofMinutes(1), Duration.ZERO);
	}

	/** Waits (up to 5 s) until the lock is free, because the tick that fires at startup holds it for its lockAtLeastFor. */
	private SimpleLock acquire(LockProvider provider, String name) throws InterruptedException {
		for (int i = 0; i < 50; i++) {
			Optional<SimpleLock> lock = provider.lock(config(name));
			if (lock.isPresent()) {
				return lock.get();
			}
			Thread.sleep(100);
		}
		throw new AssertionError("could not acquire lock " + name);
	}

	@Test
	void onlyOneInstanceCanHoldANamedLockAtATime() {
		LockProvider instanceA = anotherInstance();
		LockProvider instanceB = anotherInstance();

		SimpleLock held = instanceA.lock(config("demo-job")).orElseThrow();

		assertThat(instanceB.lock(config("demo-job"))).isEmpty();          // B must skip this round
		assertThat(instanceB.lock(config("another-job"))).isPresent();    // different job, different lock

		held.unlock();
		assertThat(instanceB.lock(config("demo-job"))).isPresent();        // once A is done, B may run it
	}

	@Test
	void theCheckSchedulerTickSkipsItsWorkWhileAnotherInstanceHoldsTheLock() throws Exception {
		SimpleLock otherInstance = acquire(lockProvider, LOCK); // "the other app copy" is mid-tick
		try {
			reset(scheduleService);
			when(scheduleService.claimDueMonitors(any())).thenReturn(List.of());

			scheduler.tick();

			verify(scheduleService, never()).claimDueMonitors(any()); // we did NOT claim: no double check
		} finally {
			otherInstance.unlock();
		}
	}

	@Test
	void theCheckSchedulerTickRunsWhenNobodyHoldsTheLock() throws Exception {
		SimpleLock free = acquire(lockProvider, LOCK); // wait out the startup tick's lock, then...
		free.unlock();                                  // ...release it again so the lock is free
		reset(scheduleService);
		when(scheduleService.claimDueMonitors(any())).thenReturn(List.of());

		scheduler.tick();

		verify(scheduleService, times(1)).claimDueMonitors(any());
	}
}
