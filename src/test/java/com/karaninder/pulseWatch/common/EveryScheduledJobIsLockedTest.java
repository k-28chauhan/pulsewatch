package com.karaninder.pulseWatch.common;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A guard for the future: every @Scheduled method in the application must also carry @SchedulerLock,
 * otherwise a job added in a later phase would silently run on EVERY instance.
 */
class EveryScheduledJobIsLockedTest {

	@Test
	void everyScheduledMethodHasASchedulerLock() throws Exception {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter((reader, factory) -> true);

		List<String> unlocked = new ArrayList<>();
		int scheduledMethods = 0;
		for (BeanDefinition definition : scanner.findCandidateComponents("com.karaninder.pulseWatch")) {
			Class<?> type = Class.forName(definition.getBeanClassName());
			for (Method method : type.getDeclaredMethods()) {
				if (AnnotationUtils.findAnnotation(method, Scheduled.class) != null) {
					scheduledMethods++;
					if (AnnotationUtils.findAnnotation(method, SchedulerLock.class) == null) {
						unlocked.add(type.getSimpleName() + "." + method.getName());
					}
				}
			}
		}

		assertThat(scheduledMethods).as("the scan must actually find the scheduled jobs").isGreaterThanOrEqualTo(2);
		assertThat(unlocked).as("@Scheduled methods without @SchedulerLock").isEmpty();
	}
}
