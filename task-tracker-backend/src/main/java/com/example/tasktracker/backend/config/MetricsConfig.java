package com.example.tasktracker.backend.config;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.View;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

@Configuration
public class MetricsConfig {
    @Bean
    public AutoConfigurationCustomizerProvider otelMetricsCustomizer() {
        return autoConfiguration -> autoConfiguration.addMeterProviderCustomizer(
                (sdkMeterProviderBuilder, configProperties) -> sdkMeterProviderBuilder.registerView(
                        InstrumentSelector.builder()
                                .setName("hikaricp.connections.acquire")
                                .build(),
                        View.builder()
                                .setAggregation(
                                        Aggregation.explicitBucketHistogram(
                                                Arrays.asList(
                                                        0.001, // 1мс
                                                        0.005, // 5мс
                                                        0.010, // 10мс
                                                        0.050, // 50мс
                                                        0.100, // 100мс
                                                        0.250, // 250мс
                                                        0.500, // 500мс
                                                        1.000, // 1с
                                                        5.000  // 5с
                                                )
                                        )
                                )
                                .build()
                )
        );
    }
}