package com.example.tasktracker.emailsender.o11y.observation.context.kafka;

import com.example.tasktracker.emailsender.o11y.observation.context.DetachedContext;
import com.example.tasktracker.emailsender.o11y.observation.convention.MessagingOperation;
import io.micrometer.observation.transport.SenderContext;
import io.micrometer.tracing.TraceContext;
import lombok.Getter;
import lombok.Setter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.lang.Nullable;

import java.nio.charset.StandardCharsets;

@Getter
@Setter
public class KafkaRecordPublishContext extends SenderContext<ProducerRecord<?, ?>>
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

    public KafkaRecordPublishContext() {
        super((carrier, key, value) -> {
            if (carrier == null)
                return;
            Headers headers = carrier.headers();
            headers.remove(key);
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        });
    }

    @Override
    public String getOperationType() {
        return MessagingOperation.Type.SEND;
    }

    @Override
    public String getOperationName() {
        return MessagingOperation.Name.PUBLISH;
    }
}
