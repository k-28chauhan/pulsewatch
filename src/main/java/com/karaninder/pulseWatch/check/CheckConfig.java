package com.karaninder.pulseWatch.check;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Wiring for the check engine: switches scheduling on and defines the two shared infrastructure beans. */
@Configuration
@EnableScheduling // without this, @Scheduled methods are silently ignored
@EnableConfigurationProperties(CheckProperties.class)
public class CheckConfig {

	/** One shared client for all checks (it is thread-safe and pools connections). */
	@Bean
	HttpClient checkHttpClient() {
		return HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NEVER) // a 301 is reported as 301, not silently followed
				.build();
	}

	/** Up to 3 attempts per check, waiting 0.5 s before the 2nd and 1 s before the 3rd. */
	@Bean
	RetryPolicy retryPolicy() {
		return new RetryPolicy(3, Duration.ofMillis(500));
	}

	@Bean
	Sleeper sleeper() {
		return Thread::sleep; // method reference: Thread.sleep(Duration) matches Sleeper.sleep(Duration)
	}

	/**
	 * Every submitted task gets its own brand-new virtual thread: no pool size to tune.
	 * destroyMethod "close" makes Spring wait for running checks when the app shuts down.
	 */
	@Bean(destroyMethod = "close")
	ExecutorService checkExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}
}
