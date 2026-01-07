package com.example.tasktracker.scheduler.consumer.dailyreport.component;

import com.example.tasktracker.scheduler.common.MdcKeys;
import com.example.tasktracker.scheduler.consumer.dailyreport.client.dto.UserTaskReport;
import com.example.tasktracker.scheduler.consumer.dailyreport.messaging.dto.EmailTriggerCommand;
import io.opentelemetry.api.trace.Span;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Компонент для преобразования доменных данных в команды на отправку уведомлений.
 */
@Component
public class DailyReportMapper {

    private static final String TEMPLATE_ID = "DAILY_TASK_REPORT";
    private static final String DEFAULT_LOCALE = "en-US";

    /**
     * Создает команду EmailTriggerCommand на основе отчета.
     * Автоматически заполняет correlationId из текущего контекста трассировки.
     *
     * @param report     Данные отчета пользователя.
     * @param reportDate Дата, за которую сформирован отчет.
     * @return Готовая к отправке команда.
     */
    public EmailTriggerCommand toCommand(UserTaskReport report, LocalDate reportDate) {
        Map<String, Object> context = new HashMap<>();
        context.put("tasksCompleted", report.tasksCompleted());
        context.put("tasksPending", report.tasksPending());
        context.put("reportDate", reportDate.toString());
        context.put("userEmail", report.email());

        String correlationId = resolveCorrelationId();

        return new EmailTriggerCommand(
                report.email(),
                TEMPLATE_ID,
                context,
                DEFAULT_LOCALE, //TODO: Use user locale from report
                report.userId(),
                correlationId
        );
    }

    private String resolveCorrelationId() {
        // 1. Пытаемся взять TraceID из текущего активного спана (OTel)
        if (Span.current().getSpanContext().isValid()) {
            return Span.current().getSpanContext().getTraceId();
        }

        // 2. Если спана нет, проверяем MDC (куда его мог положить фильтр или аспект)
        String mdcTraceId = MDC.get("trace_id"); // OTel обычно использует trace_id или traceId
        if (mdcTraceId != null) {
            return mdcTraceId;
        }

        // 3. Фолбэк на JobRunId (бизнес-идентификатор запуска)
        String jobRunId = MDC.get(MdcKeys.JOB_RUN_ID);
        if (jobRunId != null) {
            return jobRunId;
        }

        // 4. Если совсем ничего нет - генерируем новый UUID
        return UUID.randomUUID().toString();
    }
}