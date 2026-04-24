package com.example.tasktracker.emailsender.o11y.config;

import com.example.tasktracker.emailsender.config.ReliabilityProperties;
import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaRecordPublishContext;
import com.example.tasktracker.emailsender.o11y.observation.convention.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "app.observability.enabled", havingValue = "true", matchIfMissing = true)
public class ObservationConfig {
    @Bean
    public AssemblyConvention assemblyConvention() {
        return new AssemblyConvention();
    }

    @Bean
    public ChunkRateLimitConvention chunkRateLimitConvention(ReliabilityProperties properties) {
        return new ChunkRateLimitConvention(properties);
    }

    @Bean
    public EmailSmtpConvention emailSmtpConvention() {
        return new EmailSmtpConvention();
    }

    @Bean
    public KafkaProcessConvention kafkaProcessConvention() {
        return new KafkaProcessConvention();
    }

    @Bean
    public KafkaPublishConvention<KafkaRecordPublishContext> kafkaPublishConvention() {
        return new KafkaPublishConvention<>();
    }

    @Bean
    public KafkaReceiveConvention<?> kafkaReceiveConvention() {
        return new KafkaReceiveConvention<>();
    }

    @Bean
    public KafkaRejectPublishConvention kafkaRejectPublishConvention() {
        return new KafkaRejectPublishConvention();
    }

    @Bean
    public RedisConvention redisConvention() {
        return new RedisConvention();
    }
}
