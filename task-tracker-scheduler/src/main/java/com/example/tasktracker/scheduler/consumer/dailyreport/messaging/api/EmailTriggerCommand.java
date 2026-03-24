package com.example.tasktracker.scheduler.consumer.dailyreport.messaging.api;

import java.util.Map;

/**
 * Команда на отправку email.
 */
public record EmailTriggerCommand(
        String recipientEmail,
        TemplateType templateId,
        Map<String, Object> templateContext,
        String locale,
        Long userId,
        String correlationId
) {}