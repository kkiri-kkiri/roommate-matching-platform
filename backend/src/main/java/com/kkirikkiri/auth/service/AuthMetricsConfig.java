package com.kkirikkiri.auth.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthMetricsConfig {
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    MeterRegistry authMeterRegistry() { return new SimpleMeterRegistry(); }
}
