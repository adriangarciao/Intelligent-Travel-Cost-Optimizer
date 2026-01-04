package com.adriangarciao.traveloptimizer.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for application metrics using Micrometer. Provides common tags and customizations
 * for all meters.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public MeterFilter metricsCommonTags() {
        return MeterFilter.commonTags(
                io.micrometer.core.instrument.Tags.of("application", "traveloptimizer"));
    }
}
