 package com.example.tasktracker.emailsender.o11y.observation.convention;

import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaRecordProcessContext;
import io.micrometer.observation.Observation;

public class KafkaProcessConvention extends BaseKafkaMessagingConvention<KafkaRecordProcessContext> {
    @Override
    public String getName() {
        return "messaging.process.duration";
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context.getClass() == KafkaRecordProcessContext.class;
    }
}
