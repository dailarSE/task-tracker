package com.example.tasktracker.emailsender.o11y.observation.convention;

import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.opentelemetry.semconv.ErrorAttributes;

public abstract class BaseO11yConvention<T extends Observation.Context> implements ObservationConvention<T> {
    @Override
    public KeyValues getLowCardinalityKeyValues(T context) {
        if (context.getError() != null) {
            String errorType = context.getError().getClass().getCanonicalName();
            if (errorType == null) {
                errorType = context.getError().getClass().getName();
            }
            return KeyValues.of(ErrorAttributes.ERROR_TYPE.getKey(), errorType);
        }
        return KeyValues.empty();
    }
}
