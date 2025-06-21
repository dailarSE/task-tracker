// file: src/main/java/com/example/tasktracker/backend/internal/scheduler/validation/ValidReportIntervalValidator.java
package com.example.tasktracker.backend.internal.scheduler.validation;

import com.example.tasktracker.backend.internal.scheduler.config.SchedulerSupportApiProperties;
import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReportRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class ValidReportIntervalValidator implements ConstraintValidator<ValidReportInterval, UserTaskReportRequest> {

    private final Clock clock;
    private final SchedulerSupportApiProperties properties;

    @Override
    public boolean isValid(UserTaskReportRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getFrom() == null || request.getTo() == null) {
            return true; // Проверку на null делают @NotNull на полях
        }

        boolean isValid = true;
        Instant from = request.getFrom();
        Instant to = request.getTo();

        if (!from.isBefore(to)) {
            isValid = false;
            addConstraintViolation(context, "{internal.report.validation.fromBeforeTo}");
        }

        long maxIntervalDays = properties.getUserTaskReport().getMaxIntervalDays();
        if (Duration.between(from, to).toDays() > maxIntervalDays) {
            isValid = false;
            addConstraintViolation(context, "{internal.report.validation.intervalTooLarge}", "to", maxIntervalDays);
        }

        long maxAgeDays = properties.getUserTaskReport().getMaxAgeDays();
        if (to.isBefore(clock.instant().minus(maxAgeDays, ChronoUnit.DAYS))) {
            isValid = false;
            addConstraintViolation(context, "{internal.report.validation.intervalTooOld}", "to", maxAgeDays);
        }

        return isValid;
    }

    private void addConstraintViolation(ConstraintValidatorContext context, String messageTemplate, Object... args) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(String.format(messageTemplate, args))
                .addConstraintViolation();
    }

    private void addConstraintViolation(ConstraintValidatorContext context, String messageTemplate, String propertyNode, Object... args) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(String.format(messageTemplate, args))
                .addPropertyNode(propertyNode)
                .addConstraintViolation();
    }
}