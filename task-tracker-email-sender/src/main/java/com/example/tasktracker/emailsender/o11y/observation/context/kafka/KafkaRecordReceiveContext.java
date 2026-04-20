package com.example.tasktracker.emailsender.o11y.observation.context.kafka;

import com.example.tasktracker.emailsender.messaging.util.KafkaHeaderReader;
import com.example.tasktracker.emailsender.o11y.observation.context.DetachedContext;
import com.example.tasktracker.emailsender.o11y.observation.convention.MessagingOperation;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.tracing.TraceContext;
import lombok.Getter;
import lombok.Setter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
public class KafkaRecordReceiveContext extends ReceiverContext<ConsumerRecord<?, ?>>
        implements KafkaConnectionContext, KafkaMessageContext, DetachedContext {

    private TraceContext remoteParentLink;
    // Low Cardinality
    private String clientId;
    @Nullable
    private String consumerGroup;
    private String serverAddress;
    private Integer serverPort;
    private String topic;
    // High Cardinality
    private String messageId;
    private String conversationId;
    private String partition;
    private String offset;
    // Opt-in
    @Nullable
    private Long bodySize;
    @Nullable
    private Long envelopeSize;

    public KafkaRecordReceiveContext() {
        super((carrier, key) ->
                KafkaHeaderReader.readAsString(carrier, key).orElse(null));
    }

    @Override
    public String getOperationType() {
        return MessagingOperation.Type.RECEIVE;
    }

    @Override
    public String getOperationName() {
        return MessagingOperation.Name.POLL;
    }
}
