package com.example.tasktracker.emailsender.o11y.observation.convention;

import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaBatchReceiveContext;
import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaConnectionContext;
import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaRecordReceiveContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.transport.ReceiverContext;

public class KafkaReceiveConvention<T extends ReceiverContext<?> & KafkaConnectionContext>
        extends BaseKafkaMessagingConvention<T> {
    @Override
    public String getName() {
        return "messaging.client.operation.duration";
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context.getClass() == KafkaRecordReceiveContext.class || context.getClass() == KafkaBatchReceiveContext.class;
    }
}
