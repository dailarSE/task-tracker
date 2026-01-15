package com.example.tasktracker.scheduler.consumer.dailyreport.config;

import com.example.tasktracker.scheduler.consumer.dailyreport.DailyTaskReportConsumer;
import com.example.tasktracker.scheduler.consumer.dailyreport.client.TaskReportFetcherClient;
import com.example.tasktracker.scheduler.consumer.dailyreport.component.DailyReportBatchProcessor;
import com.example.tasktracker.scheduler.consumer.dailyreport.component.DailyReportMapper;
import com.example.tasktracker.scheduler.consumer.dailyreport.component.DltPublisher;
import com.example.tasktracker.scheduler.consumer.dailyreport.component.EmailCommandPublisher;
import com.example.tasktracker.scheduler.consumer.dailyreport.messaging.dto.EmailTriggerCommand;
import com.example.tasktracker.scheduler.job.dailyreport.messaging.event.UserSelectedForDailyReportEvent;
import com.example.tasktracker.scheduler.metrics.MetricsReporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Configuration
@EnableConfigurationProperties(DailyReportConsumerProperties.class)
@ConditionalOnProperty(name = "app.scheduler.consumers.daily-report.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class DailyReportConsumerConfiguration {

    private final DailyReportConsumerProperties consumerProperties;

    @Bean
    public DailyReportMapper dailyReportMapper() {
        return new DailyReportMapper();
    }

    @Bean
    public DltPublisher dltPublisher(@Qualifier("dailyReportRecoverer") DeadLetterPublishingRecoverer recoverer,
                                     MetricsReporter metrics) {
        return new DltPublisher(recoverer, consumerProperties, metrics);
    }

    @Bean
    public EmailCommandPublisher emailCommandPublisher(KafkaTemplate<String, EmailTriggerCommand> kafkaTemplate,
                                                       DailyReportConsumerProperties dailyReportConsumerProperties,
                                                       MetricsReporter metrics) {
        return new EmailCommandPublisher(kafkaTemplate, dailyReportConsumerProperties, metrics);
    }

    @Bean
    public DailyReportBatchProcessor dailyReportBatchProcessor(TaskReportFetcherClient fetcherClient,
                                                               DailyReportMapper mapper,
                                                               EmailCommandPublisher publisher,
                                                               DltPublisher dltPublisher,
                                                               MetricsReporter metrics) {
        return new DailyReportBatchProcessor(fetcherClient, mapper, publisher, dltPublisher, metrics);
    }

    @Bean
    public DailyTaskReportConsumer dailyTaskReportConsumer(DailyReportBatchProcessor batchProcessor,
                                                           MetricsReporter metrics) {
        return new DailyTaskReportConsumer(batchProcessor, metrics);
    }

    @Bean
    public TaskReportFetcherClient taskReportFetcherClient(RestClient restClient) {
        return new TaskReportFetcherClient(restClient);
    }

    @Bean("dailyReportRecoverer")
    public DeadLetterPublishingRecoverer dailyReportRecoverer(
            KafkaTemplate<Object, Object> kafkaTemplate) {

        return new DeadLetterPublishingRecoverer(kafkaTemplate);
    }

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
            @Qualifier("dailyReportRecoverer") DeadLetterPublishingRecoverer recoverer) {

        var factory = new ConcurrentKafkaListenerContainerFactory<String, UserSelectedForDailyReportEvent>();
        factory.setConsumerFactory(dailyReportConsumerFactory);

        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);

        factory.setConcurrency(consumerProperties.getConcurrency());
        factory.getContainerProperties().setPollTimeout(consumerProperties.getPollTimeout().toMillis());

        var retryConfig = consumerProperties.getRetryAndDlt();
        if (retryConfig.isEnabled()) {
            ExponentialBackOff backOff = new ExponentialBackOffWithMaxRetries(retryConfig.getMaxAttempts() - 1);

            backOff.setInitialInterval(retryConfig.getInitialIntervalMs());
            backOff.setMultiplier(retryConfig.getMultiplier());
            backOff.setMaxInterval(retryConfig.getMaxIntervalMs());
            backOff.setMaxAttempts(retryConfig.getMaxAttempts());

            ConsumerRecordRecoverer recovererForErrorHandler;
            if (retryConfig.getDlt().isEnabled()) {
                recovererForErrorHandler = recoverer;
                log.info("DLT is ENABLED for Daily Report Consumer.");
            } else {
                recovererForErrorHandler = (record, ex) ->
                        log.error("Global Retry Exhausted. DLT disabled. Record dropped: {}", record.key(), ex);
                log.info("DLT is DISABLED for Daily Report Consumer.");
            }
            DefaultErrorHandler errorHandler = new DefaultErrorHandler(recovererForErrorHandler, backOff);
            factory.setCommonErrorHandler(errorHandler);
        }

        return factory;
    }
}
