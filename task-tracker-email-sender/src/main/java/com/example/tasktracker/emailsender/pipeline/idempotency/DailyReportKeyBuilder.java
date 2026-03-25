package com.example.tasktracker.emailsender.pipeline.idempotency;

import com.example.tasktracker.emailsender.api.messaging.TemplateType;
import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Формирует ключ на основе идентификатора шаблона {@code templateId}, ID пользователя {@code userId}
 * и даты отчета из динамического контекста.
 * <p>
 * Применяется для разделения событий по дате. Значение даты извлекается из мапы {@code templateContext}
 * по ключу {@code reportDate}.
 * </p>
 *
 * <b>Формат ключа:</b> {@code email:dedup:{templateId}:{userId}:{reportDate}}
 */
@Component
public class DailyReportKeyBuilder implements TemplateKeyBuilder {

    private static final String KEY_PREFIX = "email:dedup:";
    private static final String CONTEXT_DATE_KEY = "reportDate";

    @Override
    public boolean supports(TemplateType type) {
        return type == TemplateType.DAILY_TASK_REPORT;
    }

    /**
     * @param command команда на отправку email.
     * @return ключ дедупликации.
     * @throws IllegalArgumentException если в {@code templateContext} отсутствует ключ "reportDate"
     * или его значение является пустым/null.
     */
    @Override
    public String build(TriggerCommand command) {
        Object dateObj = command.templateContext().get(CONTEXT_DATE_KEY);

        if (dateObj == null || !StringUtils.hasText(String.valueOf(dateObj))) {
            throw new IllegalArgumentException("Context key '" + CONTEXT_DATE_KEY + "' is missing for " +
                    TemplateType.DAILY_TASK_REPORT + " key generation.");
        }

        String dateString = String.valueOf(dateObj);

        return KEY_PREFIX +
                TemplateType.DAILY_TASK_REPORT.toString().toLowerCase() +
                ":" +
                command.userId() +
                ":" +
                dateString;
    }
}