package com.karaninder.pulseWatch.common;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Makes @SchedulerLock work: before a locked job runs, ShedLock tries to take its row in the shedlock table.
 * With several app instances, exactly one wins each round and the others skip the job.
 * defaultLockAtMostFor is the safety net: if the winner crashes while holding the lock, it expires after this long.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

	@Bean
	LockProvider lockProvider(DataSource dataSource) {
		return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
				.withJdbcTemplate(new JdbcTemplate(dataSource))
				.usingDbTime() // the DATABASE's clock decides, so instances with different clocks cannot disagree
				.build());
	}
}
