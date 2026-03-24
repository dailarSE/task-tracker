package com.example.tasktracker.scheduler.consumer.dailyreport.messaging.api;

/**
 * Заголовки сервисов-отправителей.
 */
public class MessagingHeaders {
    /**
     * Обязательный заголовок: тип шаблона (USER_WELCOME, и т.д.)
     */
    public static final String X_TEMPLATE_ID = "X-Template-ID";

    /**
     * Обязательный заголовок: уникальный идентификатор сообщения.
     */
    public static final String X_CORRELATION_ID = "X-Correlation-ID";

    /**
     * Опциональный заголовок: до какого момента времени (ISO-8601) сообщение актуально.
     * Если не указан - используются настройки по умолчанию для типа шаблона.
     */
    public static final String X_VALID_UNTIL = "X-Valid-Until";
}
