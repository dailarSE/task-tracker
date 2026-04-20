package com.example.tasktracker.emailsender.o11y.kafka;

import com.example.tasktracker.emailsender.o11y.observation.context.DetachedContext;
import com.example.tasktracker.emailsender.o11y.observation.context.KafkaContextFactory;
import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaRecordPublishContext;
import com.example.tasktracker.emailsender.o11y.observation.convention.KafkaPublishConvention;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.util.Assert;

import java.util.concurrent.CompletableFuture;

/**
 * Реализация {@link KafkaTemplate}, обеспечивающая гранулярную обсервабилити
 * в соответствии со спецификацией OpenTelemetry для систем обмена сообщениями.
 * <p>
 * 1. <b>Span Links вместо Parent-Child:</b> В отличие от стандартной инструментации
 *    Spring Kafka, поддерживает концепцию {@link DetachedContext}. Это
 *    позволяет использовать links вместо вложенности, предотвращая
 *    "Trace Explosion" в длинных цепочках продюсер-консьюмер.
 * 2. <b>Точность метрик (Post-ACK metadata):</b> Жизненный цикл наблюдения синхронизирован
 *    с сетевыми коллбэками Kafka. Это гарантирует захват координат
 *    сообщения (partition, offset) <b>после</b> подтверждения брокером, но <b>до</b>
 *    фиксации метрик и закрытия спана.
 * 3. <b>Контекстное разделение:</b> Автоматически выбирает тип контекста (Publish vs Reject)
 *    на основе метаданных сообщения, что позволяет разделять телеметрию.
 */
public class ObservedKafkaTemplate extends KafkaTemplate<byte[], byte[]> {

    private final ObservationRegistry registry;
    private final KafkaContextFactory contextFactory;
    private final KafkaPublishConvention<KafkaRecordPublishContext> kafkaPublishConvention;

    public ObservedKafkaTemplate(
            ProducerFactory<byte[], byte[]> producerFactory,
            ObservationRegistry registry,
            KafkaContextFactory contextFactory,
            KafkaPublishConvention<KafkaRecordPublishContext> kafkaPublishConvention) {
        super(producerFactory);
        this.registry = registry;
        this.contextFactory = contextFactory;
        this.kafkaPublishConvention = kafkaPublishConvention;
        this.setObservationEnabled(false);
        this.setMicrometerEnabled(false);
    }

    @Override
    public CompletableFuture<SendResult<byte[], byte[]>> send(ProducerRecord<byte[], byte[]> record) {
        Assert.notNull(record, "'record' cannot be null");
        return managedObserve(record);
    }

    @Override
    public CompletableFuture<SendResult<byte[], byte[]>> send(String topic, byte[] data) {
        return managedObserve(new ProducerRecord<>(topic, data));
    }

    @Override
    public CompletableFuture<SendResult<byte[], byte[]>> send(String topic, byte[] key, byte[] data) {
        return managedObserve(new ProducerRecord<>(topic, key, data));
    }

    @Override
    public CompletableFuture<SendResult<byte[], byte[]>> send(String topic, Integer partition, byte[] key, byte[] data) {
        return managedObserve(new ProducerRecord<>(topic, partition, key, data));
    }

    @Override
    public CompletableFuture<SendResult<byte[], byte[]>> send(String topic, Integer partition, Long timestamp, byte[] key, byte[] data) {
        return managedObserve(new ProducerRecord<>(topic, partition, timestamp, key, data));
    }

    @SuppressWarnings("unchecked")
    @Override
    public CompletableFuture<SendResult<byte[], byte[]>> send(Message<?> message) {
        ProducerRecord<byte[], byte[]> producerRecord =
                (ProducerRecord<byte[], byte[]>) getMessageConverter().fromMessage(message, getDefaultTopic());
        if (!producerRecord.headers().iterator().hasNext()) { // possibly no Jackson
            byte[] correlationId = message.getHeaders().get(KafkaHeaders.CORRELATION_ID, byte[].class);
            if (correlationId != null) {
                producerRecord.headers().add(KafkaHeaders.CORRELATION_ID, correlationId);
            }
        }
        return managedObserve(producerRecord);
    }

    /**
     * Управляет жизненным циклом Observation, координируя его с внутренним Callback-ом Kafka.
     * whenComplete отрабатывает до вызова observation.stop() в Callback-е.
     */
    private CompletableFuture<SendResult<byte[], byte[]>> managedObserve(ProducerRecord<byte[], byte[]> record) {
        KafkaRecordPublishContext context = contextFactory.createPublishContext(record);

        Observation observation = Observation.createNotStarted(
                null, kafkaPublishConvention,() -> context, registry);

        observation.start();
        try (Observation.Scope ignored = observation.openScope()) {
            return super.doSend(record, observation)
                    .whenComplete((result, throwable) -> {
                        if (result != null && result.getRecordMetadata() != null) {
                            var md = result.getRecordMetadata();
                            context.setPartition(String.valueOf(md.partition()));
                            context.setOffset(String.valueOf(md.offset()));
                        }
                    });
        } catch (RuntimeException ex) {
            if (context.getError() == null) {
                observation.error(ex);
                observation.stop();
            }
            throw ex;
        }
    }
}
