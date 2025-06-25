package com.example.tasktracker.scheduler.messaging.dto;

import java.time.LocalDate;

/**
 * Команда на обработку отчета по задачам для одного пользователя.
 * <p>
 * Отправляется в Kafka продюсером и потребляется пакетно для
 * последующего запроса отчетов.
 *
 * @param userId            ID пользователя для обработки.
 * @param jobRunId          Уникальный идентификатор конкретного запуска задачи-планировщика.
 * @param reportDate        Дата, за которую необходимо сформировать отчет.
 */
public record UserIdForProcessingCommand(
        long userId,
        String jobRunId,
        LocalDate reportDate
) {}