package com.example.tasktracker.scheduler.job.dailyreport.messaging.event;

import java.time.LocalDate;

/**
 * Событие, сигнализирующее о том, что пользователь был выбран для включения
 * в процесс формирования ежедневных отчетов.
 * <p>
 * Отправляется в Kafka продюсером и потребляется пакетно для
 * последующего запроса отчетов.
 *
 * @param userId            ID пользователя для обработки.
 * @param jobRunId          Уникальный идентификатор сгенерировавшей ивент джобы.
 * @param reportDate        Дата, за которую необходимо сформировать отчет.
 */
public record UserSelectedForDailyReportEvent(
        long userId,
        String jobRunId,
        LocalDate reportDate
) {}