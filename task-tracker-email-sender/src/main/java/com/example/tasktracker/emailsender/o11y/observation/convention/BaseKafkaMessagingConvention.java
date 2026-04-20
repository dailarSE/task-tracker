package com.example.tasktracker.emailsender.o11y.observation.convention;

import com.example.tasktracker.emailsender.o11y.observation.context.BatchContext;
import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaConnectionContext;
import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaMessageContext;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;

import static com.example.tasktracker.emailsender.o11y.observation.convention.KafkaObservationTags.HighCardinality.*;
import static com.example.tasktracker.emailsender.o11y.observation.convention.KafkaObservationTags.LowCardinality.*;

public abstract class BaseKafkaMessagingConvention<T extends Observation.Context & KafkaConnectionContext> extends BaseO11yConvention<T> {
    /**
     * Формирует имя спана согласно OTel Spec для Messaging: {@code {messaging.operation.name} {destination}}.
     * Пример: {@code user_welcome publish}
     */
    @Override
    public String getContextualName(T context) {
        return context.getOperationName() + " " + context.getTopic();
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(T context) {
        KeyValues kvs = super.getLowCardinalityKeyValues(context).and(
                SYSTEM.asString(), "kafka",
                OP_NAME.asString(), context.getOperationName(),
                OP_TYPE.asString(), context.getOperationType(),
                DESTINATION.asString(), context.getTopic(),
                CLIENT_ID.asString(), context.getClientId(),
                SERVER_ADDRESS.asString(), context.getServerAddress(),
                SERVER_PORT.asString(), String.valueOf(context.getServerPort())
        );

        if (context.getConsumerGroup() != null) {
            kvs = kvs.and(CONSUMER_GROUP.asString(), context.getConsumerGroup());
        }

        return kvs;
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(T context) {
        KeyValues kvs = super.getHighCardinalityKeyValues(context);

        if (context instanceof KafkaMessageContext messageContext) {
            if (messageContext.getMessageId() != null) {
                kvs = kvs.and(MESSAGE_ID.asString(), messageContext.getMessageId());
            }
            if (messageContext.getConversationId() != null) {
                kvs = kvs.and(CONVERSATION_ID.asString(), messageContext.getConversationId());
            }
            if (messageContext.getOffset() != null) {
                kvs = kvs.and(OFFSET.asString(), messageContext.getOffset());
            }
            if (messageContext.getPartition() != null) {
                kvs = kvs.and(PARTITION.asString(), messageContext.getPartition());
            }
            if (messageContext.getBodySize() != null) {
                kvs = kvs.and(BODY_SIZE.asString(), String.valueOf(messageContext.getBodySize()));
            }
            if (messageContext.getEnvelopeSize() != null) {
                kvs = kvs.and(ENVELOPE_SIZE.asString(), String.valueOf(messageContext.getEnvelopeSize()));
            }
        }

        if (context instanceof BatchContext batchContext) {
            kvs = kvs.and(BATCH_SIZE.asString(), String.valueOf(batchContext.getBatchSize()));
        }

        return kvs;
    }
}
