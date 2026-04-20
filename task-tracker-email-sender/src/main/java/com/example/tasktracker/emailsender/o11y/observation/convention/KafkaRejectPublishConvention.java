package com.example.tasktracker.emailsender.o11y.observation.convention;

import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaRecordRejectContext;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;

public class KafkaRejectPublishConvention extends KafkaPublishConvention<KafkaRecordRejectContext> {
    public enum LowCardinalityTags implements KeyName {
        REJECT_REASON {
            @Override
            public String asString() {
                return "email_sender.rejection.reason";
            }
        }
    }

    public enum HighCardinalityTags implements KeyName {
        REJECT_DESCRIPTION {
            @Override
            public String asString() {
                return "email_sender.rejection.description";
            }
        }
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context.getClass() == KafkaRecordRejectContext.class;
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(KafkaRecordRejectContext context) {
        KeyValues kvs = super.getLowCardinalityKeyValues(context);

        if (context.getRejectReason() != null) {
            kvs = kvs.and(LowCardinalityTags.REJECT_REASON.asString(), context.getRejectReason().toLowerCase());
        }

        return kvs;
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(KafkaRecordRejectContext context) {
        KeyValues kvs = super.getHighCardinalityKeyValues(context);

        if (context.getRejectDescription() != null) {
            kvs = kvs.and(HighCardinalityTags.REJECT_DESCRIPTION.asString(), context.getRejectDescription().toLowerCase());
        }

        return kvs;
    }
}
