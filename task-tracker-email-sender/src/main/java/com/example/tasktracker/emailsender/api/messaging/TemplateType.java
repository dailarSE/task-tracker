package com.example.tasktracker.emailsender.api.messaging;

import lombok.NonNull;

import java.util.Arrays;

/**
 * Перечисление поддерживаемых типов шаблонов писем.
 * Используется для определения стратегии дедупликации и настроек TTL.
 */
public enum TemplateType {
    /**
     * Приветственное письмо при регистрации.
     * Стратегия дедупликации: один раз для пользователя.
     */
    USER_WELCOME,

    /**
     * Ежедневный отчет по задачам.
     * Стратегия дедупликации: один раз для пользователя за конкретную дату.
     */
    DAILY_TASK_REPORT;

    /**
     * Безопасно преобразует строковый ID шаблона в Enum.
     *
     * @param templateId строковый идентификатор (например, из Kafka сообщения).
     * @return соответствующий элемент перечисления.
     * @throws IllegalArgumentException если templateId не соответствует ни одному известному типу.
     */
    public static TemplateType from(@NonNull String templateId) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(templateId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown Email Template ID: '" + templateId + "'. Supported types: " + Arrays.toString(values())
                ));
    }
}