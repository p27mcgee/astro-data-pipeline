package com.mcgeecahill.astro.processor.config;

import com.mcgeecahill.astro.processor.util.MetricsCollector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

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
