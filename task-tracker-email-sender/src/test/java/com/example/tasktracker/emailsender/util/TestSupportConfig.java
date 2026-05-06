package com.example.tasktracker.emailsender.util;

import io.lettuce.core.ClientOptions;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Duration;

@TestConfiguration
@Import({KafkaSupport.class, RedisSupport.class, EmailSupport.class})
public class TestSupportConfig {
    @Value("${app.email.kafka-topic}")
    private String mainTopic;
    @Value("${app.email.retry-topic}")
    private String retryTopic;
    @Value("${app.email.dlt-topic}")
    private String dltTopic;

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceCustomizer() {
        return builder -> builder
                .clientOptions(ClientOptions.builder()
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .build())
                .commandTimeout(Duration.ofMillis(400));
    }

    @Bean
    public NewTopic mainTopic() {
        return new NewTopic(mainTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic retryTopic() {
        return new NewTopic(retryTopic, 1, (short) 1);
    }

    @Bean
    public NewTopic dltTopic() {
        return new NewTopic(dltTopic, 1, (short) 1);
    }
}
