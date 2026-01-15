package com.example.tasktracker.scheduler.consumer.dailyreport.messaging.dto;

import java.util.Map;

/**
 * Команда на отправку email.
 */
public record EmailTriggerCommand(
        String recipientEmail,
        String templateId,
        Map<String, Object> templateContext,
        String locale,
        Long userId,
        String correlationId
) {}