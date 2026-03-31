package com.example.tasktracker.emailsender.pipeline.dispatch;

import com.example.tasktracker.emailsender.config.EmailSenderProperties;
import com.example.tasktracker.emailsender.exception.FatalProcessingException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureSuspendedException;
import com.example.tasktracker.emailsender.messaging.util.KafkaMetadataEnricher;
import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import com.example.tasktracker.emailsender.util.AsyncUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.springframework.kafka.support.KafkaUtils.determineSendTimeout;

@Slf4j
public class DispatcherStep {
    private final KafkaMetadataEnricher metadataEnricher;
    private final KafkaTemplate<byte[], byte[]> kafkaTemplate;
    private final Duration sendTimeout;
    protected final String retryTopic;
    protected final String dltTopicName;

    public DispatcherStep(
            KafkaMetadataEnricher metadataEnricher,
            KafkaTemplate<byte[], byte[]> kafkaTemplate,
            EmailSenderProperties properties
    ) {
        this.metadataEnricher = metadataEnricher;
        this.kafkaTemplate = kafkaTemplate;
        this.retryTopic = properties.getRetryTopic();
        this.dltTopicName = properties.getDltTopic();

        this.sendTimeout = determineSendTimeout(
                kafkaTemplate.getProducerFactory().getConfigurationProperties(),
                2000L,
                5000L
        );
    }

    /**
     * Выполняет финальную маршрутизацию элементов батча на основе их терминальных статусов.
     * <p>
     * Метод работает по следующему алгоритму:
     * 1. Валидация инвариантов: все элементы должны быть обработаны (не быть в статусе PENDING).
     * 2. Проверка здоровья инфраструктуры: если хотя бы один элемент содержит {@link InfrastructureException},
     * выбрасывается {@link InfrastructureSuspendedException} для перезапуска всего батча консьюмером.
     * 3. Асинхронная отправка: элементы в статусах FAILED и RETRY отправляются в соответствующие топики Kafka.
     * 4. Синхронизация: метод ожидает завершения всех сетевых операций в пределах {@code sendTimeout}.
     * </p>
     *
     * @param batch Пакет обработанных элементов. Статус элементов SENT и SKIPPED игнорируется (ожидается Ack).
     * @throws FatalProcessingException         если обнаружен элемент в статусе PENDING или неизвестном статусе.
     * @throws InfrastructureSuspendedException если зафиксирован системный сбой (Circuit Breaker, Redis, и т.д.),
     *                                          требующий остановки обработки и повтора всего батча.
     * @throws BrokerUnavailableException       если возникла ошибка при попытке записи в Kafka (DLT/Retry топики).
     */
    public void dispatch(PipelineBatch batch) {
        List<PipelineItem> pendingItems = batch.getPendingItems();
        if (!pendingItems.isEmpty()) {
            log.error("CRITICAL: Pipeline logic error. {} items are still PENDING in dispatcher.", pendingItems.size());
            throw new FatalProcessingException(RejectReason.INTERNAL_ERROR,
                    "Batch still contains items in PENDING state during dispatching",
                    null);
        }

        List<PipelineItem> items = batch.items();

        batch.items().stream()
                .filter(PipelineItem::isFailed)
                .forEach(this::checkInfrastructureHealth);

        List<CompletableFuture<?>> routingFutures = new ArrayList<>();

        for (var item : items) {
            switch (item.getStatus()) {
                case FAILED -> routingFutures.add(handleFatal(item));
                case RETRY -> routingFutures.add(handleTransient(item));
                case SKIPPED -> log.trace("Item [{}] skipped.", item.getCoordinates());
                case SENT -> log.trace("Item [{}] successfully processed.", item.getCoordinates());
                default -> throw new FatalProcessingException(RejectReason.INTERNAL_ERROR,
                        "Unexpected value: " + item.getStatus(),
                        null);
            }
        }

        if (!routingFutures.isEmpty()) {
            try {
                CompletableFuture.allOf(routingFutures.toArray(new CompletableFuture[0]))
                        .orTimeout(sendTimeout.toMillis(), TimeUnit.MILLISECONDS)
                        .join();
                log.info("Successfully routed {} problematic items (DLT/Retry)", routingFutures.size());
            } catch (Exception e) {
                routingFutures.forEach(f -> f.cancel(true));
                log.warn("Failed to route items to DLT/Retry. Aborting.");
                throw new BrokerUnavailableException("Failed to secure problematic items in Kafka", AsyncUtils.unwrap(e));
            }
        }
    }

    protected CompletableFuture<?> handleTransient(PipelineItem item) {
        log.info("Sending item [{}] to RETRY topic. Reason: {}",
                item.getCoordinates(), item.getRejectDescription());

        ProducerRecord<byte[], byte[]> record = buildProducerRecord(retryTopic, item);
        return doSend(record);
    }

    protected CompletableFuture<?> handleFatal(PipelineItem item) {
        log.warn("Sending item [{}] to DLT. Reason: '{}'",
                item.getCoordinates(), item.getRejectDescription());

        ProducerRecord<byte[], byte[]> record = buildProducerRecord(dltTopicName, item);
        return doSend(record);
    }

    protected ProducerRecord<byte[], byte[]> buildProducerRecord(String topic, PipelineItem item) {
        ConsumerRecord<byte[], byte[]> orig = item.getOriginalRecord();
        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(topic,
                null,
                orig.key(),
                orig.value(),
                new RecordHeaders(orig.headers()));

        metadataEnricher.enrichWithFailureMetadata(record.headers(), orig, item.getRejectCause());
        return record;
    }

    protected CompletableFuture<SendResult<byte[], byte[]>> doSend(ProducerRecord<byte[], byte[]> record) {
        return kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        if (ex instanceof CancellationException) {
                            log.debug("Routing task for key {} was cancelled (Batch Aborted).", decodeKey(record.key()));
                            return;
                        }

                        Throwable cause = AsyncUtils.unwrap(ex);
                        log.error("CRITICAL: Kafka routing failure! Topic: {}, Key: {}. Reason: {}",
                                record.topic(),
                                decodeKey(record.key()),
                                cause.getMessage());
                    } else {
                        log.trace("Route to {} successful. Offset: {}",
                                record.topic(), result.getRecordMetadata().offset());
                    }
                });
    }

    private void checkInfrastructureHealth(PipelineItem item) {
        Throwable cause = item.getRejectCause();
        if (cause == null) return;

        Throwable root = cause;
        while (root != null) {
            if (root instanceof InfrastructureException infra) {
                log.warn("INFRASTRUCTURE FAILURE DETECTED at [{}]. Reason: '{}'. Triggering whole batch retry.",
                        item.getCoordinates(), infra.getMessage());
                throw new InfrastructureSuspendedException("Infrastructure failure: " + infra.getMessage(), infra);
            }
            root = root.getCause();
        }
    }

    private String decodeKey(byte[] key) {
        if (key == null) return "null";
        return new String(key, StandardCharsets.UTF_8);
    }
}
