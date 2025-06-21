package com.example.tasktracker.backend.internal.scheduler.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ValidReportIntervalValidator.class)
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidReportInterval {
    String message() default "{internal.report.validation.invalidInterval.default}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}