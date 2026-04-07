package com.example.tasktracker.emailsender.config;

import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureException;
import com.example.tasktracker.emailsender.exception.infrastructure.InfrastructureSuspendedException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationSupport;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;

@Configuration
public class PipelineRetryConfig extends RetryTopicConfigurationSupport {

    @Override
    protected void configureBlockingRetries(BlockingRetriesConfigurer blockingRetries) {
        blockingRetries
                .retryOn(InfrastructureException.class, InfrastructureSuspendedException.class)
                .backOff(new FixedBackOff(30000L, FixedBackOff.UNLIMITED_ATTEMPTS));
    }

    /**
     * 2. НЕБЛОКИРУЮЩИЕ РЕТРАИ (Topic Hopping)
     * Это замена аннотации @RetryableTopic. Явное описание маршрутов.
     */
    @Bean
    public RetryTopicConfiguration emailRetryTopicConfig(
            @Qualifier("rawKafkaTemplate") KafkaTemplate<byte[], byte[]> kafkaTemplate,
            EmailSenderProperties properties) {

        return RetryTopicConfigurationBuilder
                .newInstance()
                .exponentialBackoff(60_000, 1.98, Duration.ofHours(4).toMillis())
                .maxAttempts(10)
                .includeTopic(properties.getRetryTopic())
                .retryOn(RetryableProcessingException.class)
                .listenerFactory("rawSingleRetryFactory")
                .create(kafkaTemplate);
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("kafka-retry-");
        scheduler.setPoolSize(2);
        scheduler.initialize();
        return scheduler;
    }
}