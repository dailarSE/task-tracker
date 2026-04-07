package com.example.tasktracker.emailsender.pipeline.assembler;

import com.example.tasktracker.emailsender.api.messaging.TriggerCommand;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;
import com.example.tasktracker.emailsender.pipeline.assembler.processor.*;
import com.example.tasktracker.emailsender.pipeline.model.PipelineBatch;
import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.pipeline.model.RejectReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.List;

/**
 * <p> Fail-fast ассемблер сообщений с конвейерной обработкой:
 * 1. <b>Pre-validation:</b> Проверка заголовков, TTL и формата до парсинга JSON.
 * 2. <b>Mapping:</b> Десериализация payload в {@link TriggerCommand}.
 * 3. <b>Post-validation:</b> Валидация схемы (JSR-303) и проверка консистентности данных. </p>
 * Ошибки инфраструктуры прерывают обработку батча, ошибки валидации помечают
 * конкретный {@link PipelineItem} как FAILED.
 */
@RequiredArgsConstructor
@Slf4j
public class ValidatingBatchAssembler implements BatchAssembler {

    private final MetadataResolver metadataResolver;
    private final CorrelationIdFilter correlationIdFilter;
    private final TemplateTypeProcessor typeProcessor;
    private final TtlFormatProcessor ttlFormatProcessor;
    private final TtlFilter ttlFilter;
    private final JsonParser jsonParser;
    private final Jsr303Filter jsr303Filter;
    private final ConsistencyFilter consistencyFilter;

    @Override
    public PipelineBatch assemble(List<ConsumerRecord<byte[], byte[]>> records) throws InfrastructureException {
        List<PipelineItem> items = records.stream()
                .map(PipelineItem::new)
                .toList();

        processItems(items, metadataResolver);
        processItems(items, correlationIdFilter);
        processItems(items, typeProcessor);
        processItems(items, ttlFormatProcessor);
        processItems(items, ttlFilter);

        processItems(items, jsonParser);

        processItems(items, jsr303Filter);
        processItems(items, consistencyFilter);

        return new PipelineBatch(items);
    }

    private void processItems(List<PipelineItem> items, ItemProcessor step) {
        for (var item : items) {
            if (item.getStage().isPending()) {
                try {
                    step.process(item);
                } catch (InfrastructureException e) {
                    String componentName = step.getClass().getSimpleName();
                    log.warn("Infrastructure failure during {} at {}.",
                            componentName, item.getCoordinates());
                    throw e;
                } catch (Exception e) {
                    String componentName = step.getClass().getSimpleName();
                    item.tryReject(
                            PipelineItem.Status.FAILED,
                            RejectReason.INTERNAL_ERROR,
                            String.format("Fault in component [%s]", componentName),
                            e
                    );
                    log.error("Pipeline Invariant Violation: {} failed at {} with {}",
                            componentName, item.getCoordinates(), e.getMessage(), e);
                }
            }
        }
    }
}
