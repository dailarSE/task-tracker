package com.example.tasktracker.emailsender.o11y.observation.annotation;

import java.lang.annotation.*;

/**
 * Маркер для декларативной настройки обсервабилити {@link java.util.concurrent.ExecutorService}.
 * Позволяет управлять регистрацией метрик Micrometer и механизмами проброса контекста (Tracing, MDC).
 *
 * @see com.example.tasktracker.emailsender.o11y.config.ExecutorObservabilityEnabler
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ObservedExecutor {

    /**
     * Идентификатор экзекьютора. Используется как значение тега 'name' в метриках.
     */
    String value();

    /**
     * Флаг регистрации стандартных метрик {@link io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics}.
     * Включает сбор данных об активных потоках, размере очереди и выполненных задачах.
     */
    boolean metrics() default true;

    /**
     * Флаг активации {@link io.micrometer.context.ContextExecutorService}.
     * Обеспечивает захват (snapshot) текущего контекста (TraceId, MDC, Security)
     * и его восстановление внутри потока выполнения задачи.
     */
    boolean propagation() default true;
}