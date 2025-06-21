package com.example.tasktracker.backend.internal.scheduler.validation;

import com.example.tasktracker.backend.internal.scheduler.config.SchedulerSupportApiProperties;
import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReportRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
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
            return true;
        }

        boolean isValid = true;
        Instant from = request.getFrom();
        Instant to = request.getTo();

        HibernateConstraintValidatorContext hibernateContext = context.unwrap(HibernateConstraintValidatorContext.class);

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