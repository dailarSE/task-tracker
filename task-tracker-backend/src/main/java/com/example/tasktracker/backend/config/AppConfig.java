package com.example.tasktracker.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;


/**
 * Основной конфигурационный класс приложения.
 * Содержит общие бины, используемые в различных частях приложения.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class AppConfig {
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
     *         на основе предоставленного {@link Clock}.
     */
    @Bean(name = "auditingDateTimeProvider")
    public DateTimeProvider dateTimeProvider(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }

    /**
     * Предоставляет кастомизированный {@link ObjectMapper}.
     * <p>
     * Настраивает ObjectMapper для:
     * <ul>
     *     <li>Использования {@link PropertyNamingStrategies.SnakeCaseStrategy} для преобразования
     *         имен полей Java (camelCase) в JSON (snake_case) и обратно.</li>
     *     <li>Регистрации {@link JavaTimeModule} для корректной сериализации/десериализации
     *         типов даты и времени из пакета {@code java.time} (например, {@code Instant})
     *         в стандартный формат ISO 8601.</li>
     * </ul>
     * Аннотация {@code @Primary} указывает, что этот бин ObjectMapper должен
     * использоваться по умолчанию, если Spring требуется инжектировать ObjectMapper.
     * </p>
     *
     * @return Сконфигурированный экземпляр {@link ObjectMapper}.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        objectMapper.registerModule(new JavaTimeModule());

        return objectMapper;
    }
}
