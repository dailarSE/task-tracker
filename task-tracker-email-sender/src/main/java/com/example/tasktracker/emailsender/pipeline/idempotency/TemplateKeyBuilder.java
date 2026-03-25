package com.example.tasktracker.emailsender.pipeline.idempotency;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;

/**
 * Стратегия генерации уникального ключа дедупликации (идемпотентности) для конкретного типа email-сообщения.
 */
public interface TemplateKeyBuilder {

    /**
     * Проверяет, подходит ли данная стратегия для указанного типа.
     */
    boolean supports(TemplateType type);

    /**
     * Генерирует уникальный ключ дедупликации на основе данных команды.
     */
    String build(TriggerCommand command);
}