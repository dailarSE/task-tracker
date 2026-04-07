package com.example.tasktracker.emailsender.api.messaging;

/**
 * Заголовки должны устанавливаться сервисами-отправителями.
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

    /**
     * Внутренний/Диагностический: Причина, по которой сообщение было
     * отправлено в DLT или Retry топик.
     */
    public static final String X_REJECT_REASON = "X-Reject-Reason";

    /**
     * Внутренний/Диагностический: Описание причины, по которой сообщение было
     * отправлено в DLT или Retry топик.
     */
    public static final String X_REJECT_DESCRIPTION = "X-Reject-Description";
}
