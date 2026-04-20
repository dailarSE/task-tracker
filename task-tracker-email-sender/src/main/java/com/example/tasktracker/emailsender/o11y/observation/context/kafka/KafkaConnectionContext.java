package com.example.tasktracker.emailsender.o11y.observation.context.kafka;

import org.springframework.lang.Nullable;

public interface KafkaConnectionContext {
    String getOperationType();
    String getOperationName();

    String getServerAddress();
    void setServerAddress(String serverAddress);

    Integer getServerPort();
    void setServerPort(Integer serverPort);

    String getClientId();
    void setClientId(String clientId);

    @Nullable String getConsumerGroup();
    void setConsumerGroup(@Nullable String consumerGroup);

    String getTopic();
    void setTopic(String topic);
}
