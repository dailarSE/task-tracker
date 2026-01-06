package com.example.tasktracker.scheduler.config;

import com.example.tasktracker.scheduler.common.MdcTaskDecorator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.Executor;

@Configuration
@Slf4j
public class AppConfig {

    public static final String KAFKA_CALLBACK_EXECUTOR = "kafkaCallbackExecutor";

    /**
     * Создает Executor для обработки асинхронных коллбэков от KafkaTemplate.
     * Этот Executor "знает" про MDC и пробрасывает его в потоки коллбэков.
     */
    @Bean(name = KAFKA_CALLBACK_EXECUTOR)
    public Executor kafkaCallbackExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int availableProcessors = Runtime.getRuntime().availableProcessors();

        int corePoolSize = Math.max(2, availableProcessors);
        int maxPoolSize = Math.max(corePoolSize, availableProcessors * 2);

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("kafka-cb-");

        executor.setTaskDecorator(new MdcTaskDecorator());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();

        new ExecutorServiceMetrics(
                executor.getThreadPoolExecutor(),
                KAFKA_CALLBACK_EXECUTOR,
                List.of()
        ).bindTo(meterRegistry);

        log.info("Kafka Callback Executor initialized. Core: {}, Max: {}", corePoolSize, maxPoolSize);

        return Context.taskWrapping(executor);
    }
}