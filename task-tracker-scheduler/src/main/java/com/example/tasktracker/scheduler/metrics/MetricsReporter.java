package com.example.tasktracker.scheduler.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Фасад для унифицированной работы с метриками.
 * <p>
 * Инкапсулирует логику ленивой инициализации и регистрации метрик,
 * а также предоставляет простой API для их использования в коде.
 * </p>
 */
@Component
@Slf4j
public class MetricsReporter {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Counter> countersCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> timersCache = new ConcurrentHashMap<>();

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
        String cacheKey = metric.getName() + tags.hashCode();

        return countersCache.computeIfAbsent(cacheKey, key -> {
            log.debug("Registering new counter metric: '{}' with tags: {}", metric.getName(), tags);
            return Counter.builder(metric.getName())
                    .description(metric.getDescription())
                    .tags(tags)
                    .register(meterRegistry);
        });
    }

    public Timer getTimer(Metric metric, Tags tags) {
        String cacheKey = metric.getName() + tags.hashCode();
        return timersCache.computeIfAbsent(cacheKey, key -> {
            log.debug("Registering new timer metric: '{}' with tags: {}", metric.getName(), tags);
            return Timer.builder(metric.getName())
                    .description(metric.getDescription())
                    .tags(tags)
                    .publishPercentiles(0.5, 0.95, 0.99)
                    .register(meterRegistry);
        });
    }

}