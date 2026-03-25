package com.example.tasktracker.emailsender.pipeline.idempotency;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import org.springframework.stereotype.Component;

/**
 * Формирует ключ на основе идентификатора шаблона {@code templateId} и ID пользователя {@code userId}.
 * <p>
 * Применяется, когда уникальность сообщения определяется исключительно
 * идентификатором шаблона и получателем. Любые другие данные из
 * {@code templateContext} при формировании ключа игнорируются.
 * </p>
 *
 * <b>Формат ключа:</b> {@code email:dedup:{templateId}:{userId}}
 */
@Component
public class UserWelcomeKeyBuilder implements TemplateKeyBuilder {
    private static final String KEY_PREFIX = "email:dedup:";

    @Override
    public boolean supports(TemplateType type) {
        return type == TemplateType.USER_WELCOME;
    }

    @Override
    public String build(TriggerCommand command) {
        return KEY_PREFIX +
                TemplateType.USER_WELCOME.toString().toLowerCase() +
                ":" +
                command.userId();
    }

}
