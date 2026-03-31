package com.example.tasktracker.emailsender.config;

import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean("byteArrayProducerFactory")
    public ProducerFactory<byte[], byte[]> producerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
        return new DefaultKafkaProducerFactory<>(props, new ByteArraySerializer(), new ByteArraySerializer());
    }

    @Bean("rawKafkaTemplate")
    public KafkaTemplate<byte[], byte[]> kafkaTemplate(@Qualifier("byteArrayProducerFactory") ProducerFactory<byte[], byte[]> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
