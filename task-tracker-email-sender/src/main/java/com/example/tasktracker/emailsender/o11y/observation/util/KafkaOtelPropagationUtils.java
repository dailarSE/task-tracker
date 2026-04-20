package com.example.tasktracker.emailsender.o11y.observation.util;

import com.example.tasktracker.emailsender.o11y.observation.context.DetachedContext;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.otel.bridge.OtelTraceContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import lombok.experimental.UtilityClass;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

/**
 * Утилита для ручного управления context propagation в Kafka с использованием моста OpenTelemetry.
 */
@UtilityClass
public class KafkaOtelPropagationUtils {

    private static final TextMapGetter<Headers> KAFKA_HEADER_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Headers carrier) {
            return StreamSupport.stream(carrier.spliterator(), false)
                    .map(Header::key)
                    .toList();
        }

        @Override
        public String get(Headers carrier, String key) {
            if (carrier == null)
                return null;

            Header header = carrier.lastHeader(key);
            if (header == null || header.value() == null) {
                return null;
            }

            return new String(header.value(), StandardCharsets.UTF_8);
        }
    };

    /**
     * Извлекает контекст продюсера из заголовков Kafka.
     */
    public static TraceContext extract(Headers headers, TextMapPropagator propagator) {
        Context producerOtelContext = propagator.extract(Context.root(), headers, KAFKA_HEADER_GETTER);

        SpanContext producerOtelSpanContext = Span.fromContext(producerOtelContext).getSpanContext();

        if (!producerOtelSpanContext.isValid()) {
            return null;
        }
        return OtelTraceContext.fromOtel(producerOtelSpanContext);
    }

    public static void addLinkFromHeaders(DetachedContext context, Headers headers, TextMapPropagator propagator) {
        TraceContext producerContext = extract(headers, propagator);
        context.setRemoteParentLink(producerContext);
    }
}
