package com.karaninder.pulseWatch.alert;

import com.karaninder.pulseWatch.TestcontainersConfig;
import com.karaninder.pulseWatch.check.SlowResponseDetected;
import com.karaninder.pulseWatch.incident.IncidentOpened;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Tests the WIRING that unit tests cannot see: that the listeners fire only after a commit and on another
 * thread. The real AlertService is replaced by a mock so no mail is sent.
 */
@SpringBootTest(properties = "pulsewatch.check.enabled=false")
@Import(TestcontainersConfig.class)
class AlertListenerTest {

	@Autowired
	ApplicationEventPublisher publisher;

	@Autowired
	TransactionTemplate transactions;

	@MockitoBean
	AlertService alertService;

	private final IncidentOpened opened = new IncidentOpened(1L, 2L, Instant.parse("2026-11-09T10:00:00Z"), "boom");

	@Test
	void anIncidentEventIsDeliveredAfterTheTransactionCommits() {
		transactions.executeWithoutResult(status -> publisher.publishEvent(opened));

		verify(alertService, timeout(3000)).onIncidentOpened(opened);
	}

	@Test
	void anIncidentEventFromARolledBackTransactionIsDropped() {
		transactions.executeWithoutResult(status -> {
			publisher.publishEvent(opened);
			status.setRollbackOnly(); // the incident row would not exist, so nobody must be alerted
		});

		verify(alertService, after(500).never()).onIncidentOpened(any());
	}

	@Test
	void anIncidentEventPublishedWithoutATransactionIsNotDelivered() {
		publisher.publishEvent(opened); // @TransactionalEventListener needs a transaction to attach to

		verify(alertService, after(500).never()).onIncidentOpened(any());
	}

	@Test
	void aSlowResponseEventIsHandledOnAnotherThread() throws InterruptedException {
		AtomicReference<Thread> handlerThread = new AtomicReference<>();
		CountDownLatch handled = new CountDownLatch(1);
		doAnswer(invocation -> {
			handlerThread.set(Thread.currentThread());
			handled.countDown();
			return null;
		}).when(alertService).onSlowResponse(any());

		publisher.publishEvent(new SlowResponseDetected(2L, 900, 500, Instant.now()));

		assertThat(handled.await(3, TimeUnit.SECONDS)).isTrue();
		assertThat(handlerThread.get()).isNotSameAs(Thread.currentThread());
		verify(alertService, never()).onIncidentOpened(any());
	}
}
