package com.example.tasktracker.emailsender.o11y.config;

import com.example.tasktracker.emailsender.o11y.observation.context.kafka.KafkaRecordPublishContext;
import com.example.tasktracker.emailsender.o11y.observation.convention.KafkaProcessConvention;
import com.example.tasktracker.emailsender.o11y.observation.convention.KafkaPublishConvention;
import com.example.tasktracker.emailsender.o11y.observation.convention.KafkaReceiveConvention;
import com.example.tasktracker.emailsender.o11y.observation.convention.KafkaRejectPublishConvention;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "app.observability.enabled", havingValue = "true", matchIfMissing = true)
public class ObservationConfig {

    @Bean
    public KafkaProcessConvention kafkaProcessConvention() {
        return new KafkaProcessConvention();
    }

    @Bean
    public KafkaPublishConvention<KafkaRecordPublishContext> kafkaPublishConvention(){return new KafkaPublishConvention<>();}

    @Bean
    public KafkaRejectPublishConvention kafkaRejectPublishConvention() {
        return new KafkaRejectPublishConvention();
    }

    @Bean
    public KafkaReceiveConvention<?> kafkaReceiveConvention() {
        return new KafkaReceiveConvention<>();
    }
}
