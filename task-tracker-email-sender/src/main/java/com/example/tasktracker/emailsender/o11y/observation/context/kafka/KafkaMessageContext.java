package com.example.tasktracker.emailsender.o11y.observation.context.kafka;

import org.springframework.lang.Nullable;

public interface KafkaMessageContext {
    // High Cardinality

    String getMessageId();

    void setMessageId(String messageId);

    String getConversationId();

    void setConversationId(String conversationId);

    @Nullable
    String getPartition();

    void setPartition(@Nullable String partition);

    @Nullable String getOffset();

    void setOffset(@Nullable String offset);

    //Opt-in

    @Nullable
    Long getBodySize();

    void setBodySize(@Nullable Long bodySize);

    @Nullable
    Long getEnvelopeSize();

    void setEnvelopeSize(@Nullable Long envelopeSize);
}
