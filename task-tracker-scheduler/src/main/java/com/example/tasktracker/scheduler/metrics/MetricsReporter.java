package com.example.tasktracker.scheduler.metrics;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Фасад для унифицированной работы с метриками.
 * <p>
 * Инкапсулирует логику инициализации и регистрации метрик,
 * а также предоставляет простой API для их использования в коде.
 * </p>
 */
@Component
@Slf4j
public class MetricsReporter {

    private final MeterRegistry meterRegistry;

    public MetricsReporter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementCounter(Metric metric, Tags tags) {
        incrementCounter(metric, 1.0, tags);
    }

    public void incrementCounter(Metric metric, double amount, Tags tags) {
        getCounter(metric, tags).increment(amount);
    }

    public void incrementCounter(Metric metric) {
        incrementCounter(metric, 1.0, Tags.empty());
    }

    private Counter getCounter(Metric metric, Tags tags) {
        return Counter.builder(metric.getName())
                .description(metric.getDescription())
                .tags(tags)
                .register(meterRegistry);
    }

    public Timer getTimer(Metric metric, Tags tags) {
        return Timer.builder(metric.getName())
                .description(metric.getDescription())
                .tags(tags)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    public void recordDistribution(Metric metric, double amount, Tags tags) {
        DistributionSummary.builder(metric.getName())
                .description(metric.getDescription())
                .tags(tags)
                .publishPercentileHistogram()
                .serviceLevelObjectives(10, 50, 100, 500, 1000)
                .register(meterRegistry)
                .record(amount);
    }

}