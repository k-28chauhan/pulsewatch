package com.karaninder.pulseWatch.check;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Calls one URL and reports what happened. Pure HTTP logic: no database, no Spring MVC.
 * <p>
 * Uses the JDK's built-in HttpClient rather than Spring's RestClient because every monitor has
 * its OWN timeout, and HttpClient lets us set it per request (RestClient fixes timeouts when the
 * client is built). The call blocks the current thread while waiting for the network, which is
 * fine: we run each check on its own virtual thread, where blocking is cheap.
 */
@Component
public class HttpChecker {

	private static final int MAX_ERROR_LENGTH = 500; // the DB column is VARCHAR(1000)

	private final HttpClient client;
	private final TargetGuard targetGuard;

	public HttpChecker(HttpClient client, TargetGuard targetGuard) {
		this.client = client;
		this.targetGuard = targetGuard;
	}

	public CheckOutcome check(CheckTarget target) {
		long start = System.nanoTime();
		// Checked again here, not only when the monitor is saved: the DNS answer for a hostname can change later.
		var violation = targetGuard.violation(target.url());
		if (violation.isPresent()) {
			return CheckOutcome.failed(elapsedMs(start), "Target not allowed: " + violation.get());
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(target.url()))
					.method(target.method().name(), HttpRequest.BodyPublishers.noBody())
					.timeout(Duration.ofMillis(target.timeoutMs())) // per-request deadline
					.header("User-Agent", "PulseWatch/0.1")
					.build();

			// discarding(): we only care about the status line, so don't download the body.
			HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
			return CheckOutcome.responded(response.statusCode(), elapsedMs(start), target.expectedStatus());

		} catch (HttpTimeoutException e) { // must come before IOException: it is a subclass of it
			return CheckOutcome.failed(elapsedMs(start), "Timed out after " + target.timeoutMs() + " ms");
		} catch (IOException | IllegalArgumentException e) {
			// IOException: DNS failure, connection refused, TLS error, connection reset...
			// IllegalArgumentException: URI.create() rejected a malformed URL.
			return CheckOutcome.failed(elapsedMs(start), describe(e));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt(); // restore the flag: someone asked this thread to stop
			return CheckOutcome.failed(elapsedMs(start), "Check was interrupted");
		}
	}

	private static int elapsedMs(long startNanos) {
		return (int) Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
	}

	private static String describe(Exception e) {
		String text = e.getClass().getSimpleName() + ": " + e.getMessage();
		return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
	}
}
