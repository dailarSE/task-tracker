package com.example.tasktracker.scheduler.client.retry;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Мета-аннотация для стандартизации логики повторных попыток при вызове Backend API.
 * Применяет @Retryable с предопределенной конфигурацией:
 * - Повторяет только при 5xx серверных ошибках и сетевых ошибках.
 * - Параметры (maxAttempts, backoff) читаются из application.yml.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Retryable(
        retryFor = {HttpServerErrorException.class, ResourceAccessException.class},
        maxAttemptsExpression = "${app.scheduler.backend-client.retry.max-attempts}",
        backoff = @Backoff(
                delayExpression = "${app.scheduler.backend-client.retry.initial-interval-ms}",
                maxDelayExpression = "${app.scheduler.backend-client.retry.max-interval-ms}",
                multiplierExpression = "${app.scheduler.backend-client.retry.multiplier}"
        )
)
public @interface BackendApiRetryable {
}