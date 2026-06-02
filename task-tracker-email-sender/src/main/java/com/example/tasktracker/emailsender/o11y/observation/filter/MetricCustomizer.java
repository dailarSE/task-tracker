package com.example.tasktracker.emailsender.o11y.observation.filter;

import com.example.tasktracker.emailsender.o11y.observation.convention.BaseKafkaMessagingConvention;
import com.example.tasktracker.emailsender.o11y.observation.convention.ChunkRateLimitConvention;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MetricCustomizer {
    public static final double[] DEFAULT_OTEL_BUCKET_NANOS =
            new double[]{
                    5_000_000.0,
                    10_000_000.0,
                    25_000_000.0,
                    50_000_000.0,
                    75_000_000.0,
                    100_000_000.0,
                    250_000_000.0,
                    500_000_000.0,
                    750_000_000.0,
                    1_000_000_000.0,
                    2_500_000_000.0,
                    5_000_000_000.0,
                    7_500_000_000.0,
                    10_000_000_000.0
            };

    private final ChunkRateLimitConvention chunkRateLimitConvention;

    @Bean
    public MeterFilter rateLimitMetricsFilter() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                String name = id.getName();

                if (id.getName().equals("resilience4j.circuitbreaker.calls")) {
                    return DistributionStatisticConfig.builder()
                            .percentilesHistogram(false)
                            .serviceLevelObjectives(DEFAULT_OTEL_BUCKET_NANOS)
                            .build()
                            .merge(config);
                }

                if (name.equals(chunkRateLimitConvention.getName())) {
                    return DistributionStatisticConfig.builder()
                            .percentilesHistogram(false)
                            .serviceLevelObjectives(chunkRateLimitConvention.getSloBuckets())
                            .build()
                            .merge(config);
                }

                if (name.equals("messaging.process.duration") || name.equals("messaging.client.operation.duration")) {
                    return DistributionStatisticConfig.builder()
                            .percentilesHistogram(false)
                            .serviceLevelObjectives(BaseKafkaMessagingConvention.SLO_BUCKETS)
                            .build()
                            .merge(config);
                }
                if (name.equals("jvm.gc.pause")) {
                    return DistributionStatisticConfig.builder()
                            .serviceLevelObjectives(DEFAULT_OTEL_BUCKET_NANOS)
                            .build()
                            .merge(config);
                }

                return config;
            }
        };
    }
}
