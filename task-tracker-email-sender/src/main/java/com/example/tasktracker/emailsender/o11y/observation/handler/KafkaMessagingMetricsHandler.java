package com.example.tasktracker.emailsender.o11y.observation.handler;

import com.example.tasktracker.emailsender.o11y.observation.context.BatchContext;
import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaConnectionContext;
import com.example.tasktracker.emailsender.o11y.observation.convention.MessagingOperation;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.observation.transport.SenderContext;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

@RequiredArgsConstructor
public class KafkaMessagingMetricsHandler<T extends Observation.Context & KafkaConnectionContext> implements ObservationHandler<T> {

    private final MeterRegistry meterRegistry;

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof KafkaConnectionContext &&
                (context instanceof SenderContext || context instanceof ReceiverContext);
    }

    @Override
    public void onStop(T context) {
        String op = context.getOperationType();

        int count = 1;
        if (context instanceof BatchContext batchContext)
            count = batchContext.getBatchSize();

        String metricName = resolveMessagingCounterName(op);

        if (metricName != null)
            incrementCounter(metricName, context, count);

    }

    private @Nullable String resolveMessagingCounterName(String op) {
        return switch (op) {
            case MessagingOperation.Type.RECEIVE -> "messaging.client.consumed.messages";
            case MessagingOperation.Type.SEND -> "messaging.client.sent.messages";
            default -> null;
        };
    }

    private void incrementCounter(String metricName, T context, int amount) {
        meterRegistry.counter(
                        metricName,
                        new KeyValuesTagsAdapter(context.getLowCardinalityKeyValues()))
                .increment(amount);
    }

    private record KeyValuesTagsAdapter(KeyValues keyValues) implements Iterable<Tag> {
        @Override
        public @NotNull Iterator<Tag> iterator() {
            return new Iterator<>() {
                private final Iterator<KeyValue> it = keyValues.iterator();

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Tag next() {
                    KeyValue kv = it.next();
                    return Tag.of(kv.getKey(), kv.getValue());
                }
            };
        }
    }
}
