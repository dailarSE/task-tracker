package com.example.tasktracker.scheduler.consumer.dailyreport.config;

import com.example.tasktracker.scheduler.job.dailyreport.messaging.event.UserSelectedForDailyReportEvent;
import lombok.RequiredArgsConstructor;
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
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DailyReportConsumerConfiguration {

    private final DailyReportConsumerProperties consumerProperties;

        @Bean
    public ConsumerFactory<String, UserSelectedForDailyReportEvent> dailyReportConsumerFactory(
            KafkaProperties springKafkaProperties) {

        Map<String, Object> props = springKafkaProperties.buildConsumerProperties(null);

        props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerProperties.getGroupId());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, consumerProperties.getMaxPollRecords());
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, consumerProperties.getFetchMaxWaitMs());

        JsonDeserializer<UserSelectedForDailyReportEvent> valueDeserializer =
                new JsonDeserializer<>(UserSelectedForDailyReportEvent.class, false);
        valueDeserializer.addTrustedPackages(UserSelectedForDailyReportEvent.class.getPackageName());

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    /**
     * Создает и настраивает фабрику контейнеров для batch listeners.
     */
    @Bean("dailyReportBatchContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, UserSelectedForDailyReportEvent> dailyReportBatchContainerFactory(
            ConsumerFactory<String, UserSelectedForDailyReportEvent> dailyReportConsumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, UserSelectedForDailyReportEvent>();
        factory.setConsumerFactory(dailyReportConsumerFactory);

        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);

        factory.setConcurrency(consumerProperties.getConcurrency());
        factory.getContainerProperties().setPollTimeout(consumerProperties.getPollTimeout().toMillis());

        var retryConfig = consumerProperties.getRetryAndDlt();
        if (retryConfig.isEnabled()) {
            ExponentialBackOff backOff = new ExponentialBackOff(
                    retryConfig.getInitialIntervalMs(),
                    retryConfig.getMultiplier()
            );
            backOff.setMaxInterval(retryConfig.getMaxIntervalMs());

            ConsumerRecordRecoverer recoverer;
            if (retryConfig.getDlt().isEnabled()) {
                recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
                log.info("DLT is ENABLED for Daily Report Consumer.");
            } else {
                recoverer = (record, ex) ->
                        log.error("Retries exhausted for record. DLT is disabled. Record dropped.", ex);
                log.info("DLT is DISABLED for Daily Report Consumer.");
            }

            DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
            factory.setCommonErrorHandler(errorHandler);
        }

        return factory;
    }
}
