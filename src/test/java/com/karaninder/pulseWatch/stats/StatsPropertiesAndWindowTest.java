package com.karaninder.pulseWatch.stats;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatsPropertiesAndWindowTest {

	@Test
	void retentionShorterThanSevenDaysIsRejectedBecauseTheSevenDayPercentilesNeedRawRows() {
		assertThatThrownBy(() -> new StatsProperties(Duration.ofDays(6)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("at least 7 days");
		assertThat(new StatsProperties(Duration.ofDays(7)).rawRetention()).isEqualTo(Duration.ofDays(7));
	}

	@Test
	void windowsParseFromTheirLabels() {
		assertThat(StatsWindow.fromLabel("24h")).isEqualTo(StatsWindow.LAST_24H);
		assertThat(StatsWindow.fromLabel("7d")).isEqualTo(StatsWindow.LAST_7D);
		assertThat(StatsWindow.fromLabel("30d")).isEqualTo(StatsWindow.LAST_30D);
	}

	@Test
	void anUnknownWindowIsRejectedWithAHelpfulMessage() {
		assertThatThrownBy(() -> StatsWindow.fromLabel("1y"))
				.isInstanceOf(InvalidStatsWindowException.class)
				.hasMessageContaining("'1y'")
				.hasMessageContaining("24h, 7d, 30d");
	}

	@Test
	void everyWindowIsAnExactNumberOfBuckets() {
		for (StatsWindow window : StatsWindow.values()) {
			assertThat(window.length().toSeconds() % window.bucket().toSeconds()).isZero();
		}
	}
}
