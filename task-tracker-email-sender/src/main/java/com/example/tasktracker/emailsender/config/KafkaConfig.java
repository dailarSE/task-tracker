package com.example.tasktracker.emailsender.config;

import com.example.tasktracker.emailsender.exception.RetryableProcessingException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean("defaultDltRecoverer")
    public DeadLetterPublishingRecoverer dltRecoverer(@Qualifier("rawKafkaTemplate") KafkaTemplate<byte[], byte[]> kafkaTemplate) {
        DeadLetterPublishingRecoverer deadLetterPublishingRecoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        deadLetterPublishingRecoverer.setFailIfSendResultIsError(true);

        return deadLetterPublishingRecoverer;
    }

    @Bean
    public DefaultErrorHandler errorHandler(DeadLetterPublishingRecoverer recoverer) {
        FixedBackOff backOff = new FixedBackOff(Duration.ofSeconds(60).toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS);
        DefaultErrorHandler defaultErrorHandler = new DefaultErrorHandler(recoverer, backOff);
        defaultErrorHandler.addRetryableExceptions(RetryableProcessingException.class);
        return defaultErrorHandler;
    }


    @Bean("rawBatchFactory")
    public ConcurrentKafkaListenerContainerFactory<byte[], byte[]> kafkaListenerContainerFactory(
            @Qualifier("byteArrayConsumerFactory") ConsumerFactory<byte[], byte[]> cf,
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<byte[], byte[]> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);
        factory.setBatchListener(true);

        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean("rawSingleRetryFactory")
    public ConcurrentKafkaListenerContainerFactory<byte[], byte[]> singleFactory(
            @Qualifier("byteArrayConsumerFactory") ConsumerFactory<byte[], byte[]> cf) {
        ConcurrentKafkaListenerContainerFactory<byte[], byte[]> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);
        factory.setBatchListener(false);

        return factory;
    }

    @Bean("byteArrayConsumerFactory")
    public ConsumerFactory<byte[], byte[]> consumerFactory(KafkaProperties kafkaProperties,
                                                           ObjectProvider<KafkaConnectionDetails> connectionDetailsProvider) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);

        connectionDetailsProvider.ifAvailable(details ->
                props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, details.getBootstrapServers()));

        DefaultKafkaConsumerFactory<byte[], byte[]> defaultKafkaConsumerFactory = new DefaultKafkaConsumerFactory<>(props);
        defaultKafkaConsumerFactory.setKeyDeserializerSupplier(ByteArrayDeserializer::new);
        defaultKafkaConsumerFactory.setValueDeserializerSupplier(ByteArrayDeserializer::new);

        return defaultKafkaConsumerFactory;
    }

    @Bean("byteArrayProducerFactory")
    public ProducerFactory<byte[], byte[]> producerFactory(KafkaProperties kafkaProperties,
                                                           ObjectProvider<KafkaConnectionDetails> connectionDetailsProvider) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);

        connectionDetailsProvider.ifAvailable(details ->
                props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, details.getBootstrapServers()));

        return new DefaultKafkaProducerFactory<>(props, new ByteArraySerializer(), new ByteArraySerializer());
    }

    @Bean("rawKafkaTemplate")
    public KafkaTemplate<byte[], byte[]> kafkaTemplate(@Qualifier("byteArrayProducerFactory") ProducerFactory<byte[], byte[]> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
