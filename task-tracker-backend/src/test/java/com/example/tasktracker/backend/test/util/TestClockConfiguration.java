package com.example.tasktracker.backend.test.util;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

@TestConfiguration
public class TestClockConfiguration {

    public static final Instant FIXED_TEST_INSTANT = Instant.parse("2025-07-01T10:00:00Z");

    @Bean
    @Primary // Переопределяем основной бин Clock в тестовом контексте
    public Clock testClock() {
        return Clock.fixed(FIXED_TEST_INSTANT, ZoneOffset.UTC);
    }
}