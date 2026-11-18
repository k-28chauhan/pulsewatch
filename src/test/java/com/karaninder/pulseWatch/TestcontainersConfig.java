package com.karaninder.pulseWatch;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A throwaway PostgreSQL 17 in Docker for tests that need a real database.
 * <p>
 * @ServiceConnection tells Spring Boot: "use THIS container's URL, user and password for the datasource",
 * overriding application.yaml. Flyway then runs every migration on the empty database, so these tests
 * also prove the migrations work from scratch, and they never touch your dev data.
 * Tests opt in with @Import(TestcontainersConfig.class); contexts with the same setup share one container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

	// One container for the whole test run, shared by every Spring context (starting one takes seconds).
	// Tests therefore clean up their own data: @Transactional rollback or an explicit delete in @BeforeEach.
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");

	static {
		POSTGRES.start();
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return POSTGRES;
	}
}
