package org.stsci.astro.processor.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.stsci.astro.processor.util.MetricsCollector;

@TestConfiguration
public class TestMetricsConfig {

    @Bean
    @Primary
    public MeterRegistry testMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    @Primary
    public MetricsCollector testMetricsCollector() {
        return new MetricsCollector(testMeterRegistry());
    }
}
