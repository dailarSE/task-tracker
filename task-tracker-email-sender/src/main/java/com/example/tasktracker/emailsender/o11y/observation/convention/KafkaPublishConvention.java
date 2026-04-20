package com.example.tasktracker.emailsender.o11y.observation.convention;

import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaRecordPublishContext;
import io.micrometer.observation.Observation;

public class KafkaPublishConvention<T extends KafkaRecordPublishContext> extends BaseKafkaMessagingConvention<T> {
    @Override
    public String getName() {
        return "messaging.client.operation.duration";
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context.getClass() == KafkaRecordPublishContext.class;
    }
}
