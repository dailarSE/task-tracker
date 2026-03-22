package com.example.tasktracker.emailsender.api.messaging;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Команда на отправку email, получаемая из Kafka.
 */
public record TriggerCommand(
        @Email @NotEmpty String recipientEmail,
        @NotBlank String templateId,
        @NotNull Map<String, Object> templateContext,
        @Nullable String locale,
        @NotNull Long userId,
        @NotBlank String correlationId
) {}
