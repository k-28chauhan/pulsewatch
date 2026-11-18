package com.karaninder.pulseWatch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// Runs against a throwaway Testcontainers PostgreSQL (never the dev database).
// The scheduler is switched off anyway so no real checks fire during the test.
@SpringBootTest(properties = "pulsewatch.check.enabled=false")
@Import(TestcontainersConfig.class)
class PulseWatchApplicationTests {

	@Test
	void contextLoads() {
	}

}
