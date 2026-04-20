package com.example.tasktracker.emailsender.o11y.observation.context;


import com.example.tasktracker.emailsender.config.AppProperties;
import com.example.tasktracker.emailsender.messaging.util.KafkaHeaderReader;
import com.example.tasktracker.emailsender.o11y.observation.context.kafka.*;
import com.example.tasktracker.emailsender.o11y.observation.util.KafkaOtelPropagationUtils;
import com.example.tasktracker.emailsender.o11y.observation.util.KafkaPropertiesResolver;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.example.tasktracker.emailsender.api.messaging.MessagingHeaders.*;

public class KafkaContextFactory {

    private final KafkaPropertiesResolver resolver;
    private final boolean doCaptureSizes;
    private final TextMapPropagator propagator;

    public KafkaContextFactory(KafkaPropertiesResolver resolver,
                               AppProperties appProperties,
                               TextMapPropagator propagator) {
        this.resolver = resolver;
        this.doCaptureSizes = appProperties.getObservability().isCaptureMessageSizes();
        this.propagator = propagator;
    }

    public KafkaBatchReceiveContext createBatchReceiveContext(List<ConsumerRecord<byte[], byte[]>> records) {
        return createBatchReceiveContext(records, KafkaBatchReceiveContext::new, null);
    }

    public <T extends KafkaBatchReceiveContext> T createBatchReceiveContext(List<ConsumerRecord<byte[], byte[]>> records,
                                                                            Supplier<T> contextSupplier,
                                                                            Consumer<T> dataEnhancer) {
        T context = contextSupplier.get();

        context.setCarrier(records);

        String topic = records.stream().map(ConsumerRecord::topic).distinct().count() > 1 ?
                KafkaBatchReceiveContext.MIXED_TOPIC_PLACEHOLDER :
                records.getFirst().topic();

        fillConnectionDetails(context, topic);

        context.setBatchSize(records.size());

        if (dataEnhancer != null)
            dataEnhancer.accept(context);

        return context;
    }

    public KafkaRecordReceiveContext createRecordReceiveContext(ConsumerRecord<byte[], byte[]> record) {
        return createRecordReceiveContext(record, KafkaRecordReceiveContext::new, null);
    }

    public <T extends KafkaRecordReceiveContext> T createRecordReceiveContext(ConsumerRecord<byte[], byte[]> record,
                                                                              Supplier<T> contextSupplier,
                                                                              BiConsumer<T, ConsumerRecord<byte[], byte[]>> dataEnhancer) {
        T context = contextSupplier.get();

        context.setCarrier(record);

        fillConnectionDetails(context, record.topic());

        context.setMessageId(record.topic() + "-" + record.partition() + "@" + record.offset());
        KafkaHeaderReader.readAsString(record, X_CORRELATION_ID).ifPresentOrElse(
                context::setConversationId,
                () -> context.setConversationId("unset"));
        context.setPartition(String.valueOf(record.partition()));
        context.setOffset(String.valueOf(record.offset()));

        if (doCaptureSizes) {
            context.setBodySize(calculateBodySize(record));
            context.setEnvelopeSize(calculateEnvelopeSize(record));
        }

        setLinkToRemote(record.headers(), context);

        if (dataEnhancer != null)
            dataEnhancer.accept(context, record);

        return context;
    }


    public KafkaRecordProcessContext createProcessContext(PipelineItem item) {
        return createSingleProcessContext(item, KafkaRecordProcessContext::new, null);
    }

    public <T extends KafkaRecordProcessContext> T createSingleProcessContext(PipelineItem item,
                                                                              Supplier<T> contextSupplier,
                                                                              BiConsumer<T, PipelineItem> dataEnhancer) {
        ConsumerRecord<byte[], byte[]> record = item.getOriginalRecord();

        T context = contextSupplier.get();

        fillConnectionDetails(context, record.topic());

        context.setMessageId(item.getCoordinates());
        context.setConversationId(item.getPayload().correlationId());
        context.setPartition(String.valueOf(record.partition()));
        context.setOffset(String.valueOf(record.offset()));

        if (doCaptureSizes) {
            context.setBodySize(calculateBodySize(record));
            context.setEnvelopeSize(calculateEnvelopeSize(record));
        }

        setLinkToRemote(item.getOriginalRecord().headers(), context);

        if (dataEnhancer != null)
            dataEnhancer.accept(context, item);

        return context;
    }

    public KafkaRecordPublishContext createRejectedContext(ProducerRecord<byte[], byte[]> record) {
        return createPublishContext(record, KafkaRecordRejectContext::new, (ctx, rec) -> {
            KafkaHeaderReader.readAsString(record, X_REJECT_REASON).ifPresent(ctx::setRejectReason);
            KafkaHeaderReader.readAsString(record, X_REJECT_DESCRIPTION).ifPresent(ctx::setRejectDescription);

        });
    }

    public KafkaRecordPublishContext createPublishContext(ProducerRecord<byte[], byte[]> record) {
        Optional<String> rejectionHeader = KafkaHeaderReader.readAsString(record, X_REJECT_REASON);

        return rejectionHeader.isPresent() ?
                createRejectedContext(record) :
                createPublishContext(record, KafkaRecordPublishContext::new, null);
    }

    public <T extends KafkaRecordPublishContext> T createPublishContext(ProducerRecord<byte[], byte[]> record,
                                                                        Supplier<T> contextSupplier,
                                                                        BiConsumer<T, ProducerRecord<byte[], byte[]>> dataEnhancer) {
        T context = contextSupplier.get();

        context.setCarrier(record);

        context.setClientId(resolver.getClientId());
        context.setServerAddress(resolver.getServerAddress());
        context.setServerPort(resolver.getServerPort());

        KafkaHeaderReader.readAsString(record, X_CORRELATION_ID).ifPresentOrElse(
                context::setConversationId,
                () -> context.setConversationId("unset"));

        context.setTopic(record.topic());

        if (doCaptureSizes) {
            context.setBodySize(calculateBodySize(record));
            context.setEnvelopeSize(calculateEnvelopeSize(record));
        }

        setLinkToRemote(record.headers(), context);

        if (dataEnhancer != null)
            dataEnhancer.accept(context, record);

        return context;
    }

    private <T extends DetachedContext> void setLinkToRemote(Headers headers, T context) {
        KafkaOtelPropagationUtils.addLinkFromHeaders(context, headers, propagator);
    }

    private void fillConnectionDetails(KafkaConnectionContext context, String topic) {
        context.setClientId(resolver.getClientId());
        context.setConsumerGroup(resolver.getConsumerGroup());
        context.setServerAddress(resolver.getServerAddress());
        context.setServerPort(resolver.getServerPort());
        context.setTopic(topic);
    }

// --- Счётчики байтов ---

    Long calculateBodySize(ConsumerRecord<byte[], byte[]> record) {
        return record.value() != null ? (long) record.value().length : 0L;
    }

    Long calculateBodySize(ProducerRecord<byte[], byte[]> record) {
        return record.value() != null ? (long) record.value().length : 0L;
    }

    Long calculateEnvelopeSize(ConsumerRecord<byte[], byte[]> record) {
        long size = Math.max(0, record.serializedKeySize()) + Math.max(0, record.serializedValueSize());
        for (Header header : record.headers()) {
            size += header.key().length() + (header.value() != null ? header.value().length : 0) + 8; // approx varint overhead
        }
        return size + 12; // Magic overhead
    }

    Long calculateEnvelopeSize(ProducerRecord<byte[], byte[]> record) {
        long size = calculateBodySize(record);
        if (record.key() != null) size += record.key().length;
        for (Header header : record.headers()) {
            size += header.key().length() + (header.value() != null ? header.value().length : 0) + 8;
        }
        return size;
    }
}
