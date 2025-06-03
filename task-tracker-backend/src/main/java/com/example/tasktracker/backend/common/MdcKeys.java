package com.example.tasktracker.backend.common;

/**
 * Утилитарный класс, содержащий константы для ключей Mapped Diagnostic Context (MDC).
 * Эти ключи используются для обогащения логов контекстной информацией.
 */
public final class MdcKeys {

    private MdcKeys() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Ключ MDC для хранения идентификатора пользователя (User ID).
     * Используется для корреляции логов с действиями конкретного пользователя.
     * Значение: {@value}.
     */
    public static final String USER_ID = "user.id";

    /**
     * Ключ MDC для хранения идентификатора корреляции (Correlation ID).
     * Может использоваться для сквозной трассировки операций через различные компоненты.
     * Значение: {@value}.
     */
    public static final String CORRELATION_ID = "correlation.id";

    // Можно добавить другие общие ключи MDC по мере необходимости
}