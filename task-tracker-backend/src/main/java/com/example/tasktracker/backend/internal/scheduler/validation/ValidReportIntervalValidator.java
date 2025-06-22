package com.example.tasktracker.backend.internal.scheduler.validation;

import com.example.tasktracker.backend.internal.scheduler.config.SchedulerSupportApiProperties;
import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReportRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Валидатор для кастомного ограничения {@link ValidReportInterval}.
 * <p>
 * Выполняет следующие проверки:
 * <ul>
 *     <li>Начало интервала (`from`) должно быть строго раньше его конца (`to`).</li>
 *     <li>Размер списка `userIds` не должен превышать сконфигурированный максимум.</li>
 *     <li>Длительность интервала (`to` - `from`) не должна превышать сконфигурированный максимум.</li>
 *     <li>Конечная дата интервала (`to`) не должна быть слишком "старой" относительно текущего времени.</li>
 * </ul>
 * Реализует принцип "полной валидации", сообщая обо всех нарушениях за один проход.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ValidReportIntervalValidator implements ConstraintValidator<ValidReportInterval, UserTaskReportRequest> {

    private final Clock clock;
    private final SchedulerSupportApiProperties properties;

    @Override
    public boolean isValid(UserTaskReportRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getFrom() == null || request.getTo() == null) {
            return true;
        }

        boolean isValid = true;
        Instant from = request.getFrom();
        Instant to = request.getTo();

        HibernateConstraintValidatorContext hibernateContext = context.unwrap(HibernateConstraintValidatorContext.class);

        int maxBatchSize = properties.getUserTaskReport().getMaxBatchSize();
        if (!CollectionUtils.isEmpty(request.getUserIds()) && request.getUserIds().size() > maxBatchSize) {
            isValid = false;
            addConstraintViolationWithParameter(
                    hibernateContext,
                    "{internal.report.validation.batchTooLarge}",
                    "userIds", // Привязываем ошибку к полю userIds
                    "maxSize",
                    maxBatchSize
            );
        }

        if (!from.isBefore(to)) {
            isValid = false;
            addConstraintViolation(hibernateContext, "{internal.report.validation.fromBeforeTo}", "from");
        }

        long maxIntervalDays = properties.getUserTaskReport().getMaxIntervalDays();
        if (Duration.between(from, to).toDays() > maxIntervalDays) {
            isValid = false;
            addConstraintViolationWithParameter(
                    hibernateContext,
                    "{internal.report.validation.intervalTooLarge}",
                    "to",
                    "maxDays",
                    maxIntervalDays
            );
        }

        long maxAgeDays = properties.getUserTaskReport().getMaxAgeDays();
        if (to.isBefore(clock.instant().minus(maxAgeDays, ChronoUnit.DAYS))) {
            isValid = false;
            addConstraintViolationWithParameter(
                    hibernateContext,
                    "{internal.report.validation.intervalTooOld}",
                    "to",
                    "maxAge",
                    maxAgeDays
            );
        }

        return isValid;
    }

    private void addConstraintViolation(ConstraintValidatorContext context, String messageTemplate, String propertyNode) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(messageTemplate)
                .addPropertyNode(propertyNode)
                .addConstraintViolation();
    }

    private void addConstraintViolationWithParameter(
            HibernateConstraintValidatorContext context,
            String messageTemplate,
            String propertyNode,
            String paramName,
            Object paramValue) {

        context.disableDefaultConstraintViolation();
        context.addMessageParameter(paramName, paramValue)
                .buildConstraintViolationWithTemplate(messageTemplate)
                .addPropertyNode(propertyNode)
                .addConstraintViolation();
    }
}