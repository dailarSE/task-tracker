package com.example.tasktracker.emailsender.o11y.observation.context.kafka;

import com.example.tasktracker.emailsender.o11y.observation.context.BatchContext;
import com.example.tasktracker.emailsender.o11y.observation.context.DetachedContext;
import com.example.tasktracker.emailsender.o11y.observation.convention.MessagingOperation;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.tracing.TraceContext;
import lombok.Getter;
import lombok.Setter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.lang.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Контекст для операций с батчами Kafka.
 * <p>
 * Контракт {@link ReceiverContext} требует наличия геттера для извлечения
 * Trace-контекста. В данной реализации геттер формально указывает на первое
 * сообщение в батче.
 * <p>
 * Однако, чтобы избежать ложной привязки всей операции вычитки к трейсу одного
 * случайного сообщения, класс помечен интерфейсом {@link DetachedContext}, возвращающим null.
 * Это обязует игнорировать trace parent и начинать новый трейс.
 */
@Getter
@Setter
public class KafkaBatchReceiveContext extends ReceiverContext<List<? extends ConsumerRecord<?, ?>>>
        implements KafkaConnectionContext, BatchContext, DetachedContext {
    public static final String MIXED_TOPIC_PLACEHOLDER = "_multiple_";

    // Low Cardinality
    private String clientId;
    @Nullable
    private String consumerGroup;
    private String serverAddress;
    private Integer serverPort;
    private String topic;
    // High Cardinality
    private int batchSize;

    public KafkaBatchReceiveContext() {
        super((carrier, key) -> {
            if (carrier.isEmpty())
                return null;
            Header header = carrier.getFirst().headers().lastHeader(key);
            if (header == null || header.value() == null) {
                return null;
            }
            return new String(header.value(), StandardCharsets.UTF_8);
        });
    }

    @Override
    public String getOperationType() {
        return MessagingOperation.Type.RECEIVE;
    }

    @Override
    public String getOperationName() {
        return MessagingOperation.Name.POLL;
    }

    @Override
    public TraceContext getRemoteParentLink() {
        return null;
    }

    @Override
    public void setRemoteParentLink(TraceContext context) {
    }
}
