package com.example.tasktracker.emailsender.pipeline.dispatch;

import com.example.tasktracker.emailsender.config.EmailSenderProperties;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureSuspendedException;
import com.example.tasktracker.emailsender.messaging.util.KafkaMetadataEnricher;
import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class DispatcherStepTest {

    private final List<ProducerRecord<byte[], byte[]>> sentRecords = new ArrayList<>();
    private final String retryTopic = "retry-topic";
    private final String dltTopic = "dlt-topic";

    private final KafkaMetadataEnricher stubEnricher = new KafkaMetadataEnricher() {
        @Override
        public void enrichWithFailureMetadata(Headers target,
                                              ConsumerRecord<byte[], byte[]> source,
                                              PipelineItem.ExecutionStage stage) {
            target.add("stub-enriched", "true".getBytes());
        }
    };
    private final KafkaTemplate<byte[], byte[]> fakeKafkaTemplate = createFakeTemplate();

    private DispatcherStep dispatcher;

    @BeforeEach
    void setUp() {
        sentRecords.clear();
        EmailSenderProperties props = new EmailSenderProperties();
        props.setRetryTopic(retryTopic);
        props.setDltTopic(dltTopic);

        dispatcher = new DispatcherStep(stubEnricher, fakeKafkaTemplate, props);
    }

    @Test
    @DisplayName("dispatcherHandleRetry: Маршрутизация в Retry")
    void dispatcherHandleRetry() {
        var item = createItem(PipelineItem.Status.RETRY, RejectReason.REMOTE_ERROR, null);

        dispatcher.dispatch(new PipelineBatch(List.of(item)));

        assertEquals(1, sentRecords.size());
        assertEquals(retryTopic, sentRecords.getFirst().topic());
        assertNotNull(sentRecords.getFirst().headers().lastHeader("stub-enriched"));
    }

    @Test
    @DisplayName("dispatcherHandleFailed: Маршрутизация в DLT")
    void dispatcherHandleFailed() {
        var item = createItem(PipelineItem.Status.FAILED, RejectReason.INVALID_PAYLOAD, null);

        dispatcher.dispatch(new PipelineBatch(List.of(item)));

        assertEquals(1, sentRecords.size());
        assertEquals(dltTopic, sentRecords.getFirst().topic());
    }

    @Test
    @DisplayName("dispatcherHandleInfra: Прерывание батча при ошибке инфры")
    void dispatcherHandleInfra() {
        var infraEx = new InfrastructureException("Kafka Lag", null);
        var item = createItem(PipelineItem.Status.RETRY, RejectReason.INFRASTRUCTURE, infraEx);

        assertThrows(InfrastructureSuspendedException.class,
                () -> dispatcher.dispatch(new PipelineBatch(List.of(item))));

        assertTrue(sentRecords.isEmpty());
    }

    @Test
    @DisplayName("dispatcherFail: Батч падает при полном отказе Kafka")
    void dispatcherFail() {
        var brokenKafka = new KafkaTemplate<byte[], byte[]>(new DefaultKafkaProducerFactory<>(Map.of())) {
            @Override
            public CompletableFuture<SendResult<byte[], byte[]>> send(ProducerRecord<byte[], byte[]> record) {
                return CompletableFuture.failedFuture(new RuntimeException("Kafka is totally down"));
            }
        };

        EmailSenderProperties props = new EmailSenderProperties();
        props.setRetryTopic(retryTopic);
        props.setDltTopic(dltTopic);
        var throwingDispatcher = new DispatcherStep(stubEnricher, brokenKafka, props);

        var item = createItem(PipelineItem.Status.FAILED, RejectReason.INVALID_PAYLOAD, null);
        var batch = new PipelineBatch(List.of(item));

        assertThrows(BrokerUnavailableException.class, () -> throwingDispatcher.dispatch(batch));

        assertTrue(sentRecords.isEmpty());
    }

    private KafkaTemplate<byte[], byte[]> createFakeTemplate() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of())) {
            @Override
            public CompletableFuture<SendResult<byte[], byte[]>> send(ProducerRecord<byte[], byte[]> record) {
                sentRecords.add(record);
                TopicPartition tp = new TopicPartition(record.topic(), 0);
                RecordMetadata metadata = new RecordMetadata(tp, 0L, 0, 0L, 0, 0);

                SendResult<byte[], byte[]> result = new SendResult<>(record, metadata);
                return CompletableFuture.completedFuture(result);
            }
        };
    }

    private PipelineItem createItem(PipelineItem.Status status, RejectReason reason, Exception cause) {
        var record = new ConsumerRecord<byte[], byte[]>("topic", 0, 0, null, null);
        var item = new PipelineItem(record);
        item.tryReject(status, reason, "test", cause);
        return item;
    }
}