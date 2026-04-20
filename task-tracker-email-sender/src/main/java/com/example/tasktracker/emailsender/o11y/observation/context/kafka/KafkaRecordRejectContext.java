package com.example.tasktracker.emailsender.o11y.observation.context.kafka;

import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

@Getter
@Setter
public class KafkaRecordRejectContext extends KafkaRecordPublishContext {
    // Low Cardinality
    @Nullable
    private String rejectReason;
    // High Cardinality
    @Nullable
    private String rejectDescription;

}
