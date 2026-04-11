package com.example.tasktracker.emailsender.o11y.observation.context;

import io.micrometer.tracing.TraceContext;

/**
 * Указывает, что удаленный родитель (например, RECEIVE в Kafka) из заголовков может стать Link-ом,
 * а не Parent-ом, чтобы избежать "бесконечных" трейсов.
 */
public interface DetachedContext {

    TraceContext getRemoteParentLink();

    void setRemoteParentLink(TraceContext context);
}
