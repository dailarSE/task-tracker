package com.example.tasktracker.backend.user.messaging.dto;

import lombok.*;

import java.util.Map;

/**
 * Команда на запуск отправки email.
 * <p>
 * Это DTO (Data Transfer Object) используется для передачи информации
 * в Kafka, сигнализируя сервису рассылки о необходимости отправить email.
 * Имя класса отражает его назначение как команды, а не просто набора данных.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString // Для удобства логирования
public class EmailTriggerCommand {

    /**
     * Email адрес получателя. Используется как ключ сообщения Kafka
     * для партиционирования по получателю.
     */
    private String recipientEmail;

    /**
     * Строковый идентификатор шаблона письма (например, "USER_WELCOME").
     * Email Sender будет использовать этот ID для выбора соответствующего шаблона.
     */
    private String templateId;

    /**
     * Контекст (данные) для подстановки в шаблон письма.
     * Представляет собой карту, где ключи - это плейсхолдеры в шаблоне.
     * Пример: {@code {"userEmail": "test@example.com", "registrationDate": "2025-05-31"}}
     */
    private Map<String, Object> templateContext;

    /**
     * Языковой тег локали (например, "en-US", "ru-RU") для выбора
     * локализованной версии шаблона письма. Может быть null.
     */
    private String locale;

    /**
     * Идентификатор пользователя (если применимо), связанного с этим событием.
     * Используется для логирования и трассировки на стороне получателя.
     */
    private String userId;

    /**
     * Идентификатор корреляции. Предпочтительно использовать OpenTelemetry Trace ID,
     * если он доступен в контексте отправки, для сквозной трассировки.
     * Если Trace ID недоступен, генерируется UUID.
     */
    private String correlationId;
}