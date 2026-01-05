package com.example.tasktracker.scheduler.config;

import com.example.tasktracker.scheduler.job.dailyreport.messaging.event.UserSelectedForDailyReportEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

@Slf4j
@Configuration
public class KafkaConfig {

    /**
     * Базовая ConsumerFactory. Настроена, в том числе, базовыми параметрами KafkaProperties.
     */
    @Bean
    public ConsumerFactory<String, UserSelectedForDailyReportEvent> batchConsumerFactory(
            KafkaProperties kafkaProperties,
            SchedulerAppProperties appProperties) {

        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, appProperties.getKafka().getInternalConsumer().getGroupId());

        JsonDeserializer<UserSelectedForDailyReportEvent> valueDeserializer =
                new JsonDeserializer<>(UserSelectedForDailyReportEvent.class, false);
        valueDeserializer.addTrustedPackages(UserSelectedForDailyReportEvent.class.getPackageName());

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    /**
     * Создает и настраивает фабрику контейнеров для batch listeners.
     */
    @Bean("batchKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, UserSelectedForDailyReportEvent> batchKafkaListenerContainerFactory(
            ConsumerFactory<String, UserSelectedForDailyReportEvent> batchConsumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate,
            SchedulerAppProperties properties) {

        ConcurrentKafkaListenerContainerFactory<String, UserSelectedForDailyReportEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(batchConsumerFactory);

        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setConcurrency(properties.getKafka().getInternalConsumer().getConcurrency());

        SchedulerAppProperties.RetryAndDltProperties retryProps = properties.getKafka().getInternalConsumer().getRetryAndDlt();
        if (retryProps.isEnabled()) {

            ExponentialBackOff backOff = new ExponentialBackOff(retryProps.getInitialIntervalMs(), retryProps.getMultiplier());
            backOff.setMaxInterval(retryProps.getMaxIntervalMs());

            DefaultErrorHandler errorHandler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), backOff);
            errorHandler.addNotRetryableExceptions(HttpClientErrorException.class);

            factory.setCommonErrorHandler(errorHandler);
            log.info("Batch Kafka listener configured with Exception-Classifying ErrorHandler.");
        }

        return factory;
    }
}
