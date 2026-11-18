package com.karaninder.pulseWatch.alert;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Wiring for alerts: async support, typed properties, a clock and a thread executor for listeners. */
@Configuration
@EnableAsync // without this, @Async is silently ignored and listeners run on the caller's thread
@EnableConfigurationProperties(AlertProperties.class)
public class AlertConfig {

	/** Injected instead of calling Instant.now(), so tests can freeze time with Clock.fixed(...). */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean(destroyMethod = "close")
	ExecutorService alertExecutor() {
		return Executors.newVirtualThreadPerTaskExecutor();
	}
}
