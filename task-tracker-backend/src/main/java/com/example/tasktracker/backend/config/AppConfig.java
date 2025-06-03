package com.example.tasktracker.backend.config;

import com.example.tasktracker.backend.common.MdcTaskDecorator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.opentelemetry.context.Context;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;


/**
 * Основной конфигурационный класс приложения.
 * Содержит общие бины, используемые в различных частях приложения.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@EnableAsync
@Slf4j
public class AppConfig {

    /**
     * Имя бина для Executor'а, используемого для выполнения асинхронных операций Kafka
     * (как для {@code @Async} методов, так и для коллбэков).
     * Значение: {@value}.
     */
    public static final String KAFKA_ASYNC_OPERATIONS_EXECUTOR = "kafkaAsyncOperationsExecutor";

    /**
     * Предоставляет бин {@link Clock} для получения текущего времени.
     * Использует системные часы, работающие в UTC, для обеспечения консистентности
     * времени во всем приложении.
     * Этот бин должен инжектироваться во все компоненты, которым необходимо
     * знать текущее время, для улучшения тестируемости.
     *
     * @return Системный {@link Clock}, работающий в UTC.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Предоставляет кастомный {@link DateTimeProvider} для Spring Data JPA Auditing.
     * <p>
     * Этот провайдер использует инжектированный бин {@link Clock} (который должен быть
     * сконфигурирован для работы в UTC, например, через {@code Clock.systemUTC()})
     * для получения текущего момента времени ({@link Instant}).
     * Это позволяет Spring Data JPA Auditing использовать тот же источник времени,
     * что и остальная часть приложения, обеспечивая консистентность и тестируемость.
     * </p>
     * <p>
     * Имя этого бина ("auditingDateTimeProvider") должно совпадать со значением
     * {@code dateTimeProviderRef} в аннотации {@code @EnableJpaAuditing}.
     * </p>
     *
     * @param clock Системный {@link Clock}, инжектированный Spring.
     *              Ожидается, что он будет предоставлять время в UTC.
     * @return Экземпляр {@link DateTimeProvider}, который возвращает текущий {@link Instant}
     * на основе предоставленного {@link Clock}.
     */
    @Bean(name = "auditingDateTimeProvider")
    public DateTimeProvider dateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }


    /**
     * Создает и конфигурирует {@link Executor} для выполнения всех асинхронных
     * операций, связанных с Apache Kafka (например, для {@code @Async} методов,
     * отправляющих сообщения, и для выполнения коллбэков от {@code KafkaTemplate}).
     * <p>
     * Настройки пула потоков учитывают, что операции могут быть I/O-bound
     * (ожидание ответа от Kafka или запись в БД в fallback-сценариях).
     * </p>
     * <p>
     * Метрики для этого Executor'а явно регистрируются с помощью
     * {@link ExecutorServiceMetrics} для обеспечения их доступности в Prometheus.
     * Также используется {@link MdcTaskDecorator} и {@link Context#taskWrapping(Executor)}.
     * </p>
     *
     * @param meterRegistry Глобальный реестр метрик Micrometer для регистрации метрик Executor'а.
     * @return Сконфигурированный {@link Executor} для Kafka-операций.
     */
    @Bean(name = KAFKA_ASYNC_OPERATIONS_EXECUTOR)
    public Executor kafkaAsyncOperationsExecutor(MeterRegistry meterRegistry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        // Настройки для пула, который может обрабатывать I/O-bound задачи
        int corePoolSize = Math.max(2, availableProcessors);
        int maxPoolSize = Math.max(corePoolSize, availableProcessors * 2);

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(50); // Начальное значение, может потребовать тюнинга
        executor.setThreadNamePrefix("kafka-ops-");
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize(); // Инициализируем executor перед получением getThreadPoolExecutor()

        new ExecutorServiceMetrics(
                executor.getThreadPoolExecutor(),
                KAFKA_ASYNC_OPERATIONS_EXECUTOR,
                List.of()
        ).bindTo(meterRegistry);

        log.info("Custom Kafka Async Operations Executor '{}' initialized and metrics bound. Core: {}, Max: {}, Queue: {}",
                KAFKA_ASYNC_OPERATIONS_EXECUTOR, corePoolSize, maxPoolSize, executor.getQueueCapacity());

        return Context.taskWrapping(executor); // Оборачиваем для OTel
    }
}
