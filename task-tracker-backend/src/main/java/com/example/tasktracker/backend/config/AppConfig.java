package com.example.tasktracker.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;


/**
 * Основной конфигурационный класс приложения.
 * Содержит общие бины, используемые в различных частях приложения.
 */
@Configuration
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
}
