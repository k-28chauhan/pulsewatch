package com.karaninder.pulseWatch.stats;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StatsProperties.class)
public class StatsConfig {
}
