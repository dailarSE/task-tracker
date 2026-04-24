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
    private final ChunkRateLimitConvention chunkRateLimitConvention;

    @Bean
    public MeterFilter rateLimitMetricsFilter() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                String name = id.getName();
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

                return config;
            }
        };
    }
}
