package com.example.tasktracker.scheduler.config;

import com.example.tasktracker.scheduler.messaging.dto.UserIdForProcessingCommand;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@Slf4j
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(
            RedisConnectionFactory connectionFactory,
            @Value("${spring.application.name}") String serviceName,
            SchedulerAppProperties properties) {

        String lockKeyPrefix = "shedlock:" + serviceName + ":" + properties.getShedlock().getLockName();
        log.info("Configuring ShedLock with Redis. Lock key prefix: '{}'", lockKeyPrefix);
        return new RedisLockProvider(connectionFactory, lockKeyPrefix);
    }

    /**
     * Базовая ConsumerFactory. Наследует все общие настройки из spring.kafka.consumer.*
     */
    @Bean
    public ConsumerFactory<String, UserIdForProcessingCommand> batchConsumerFactory(
            KafkaProperties kafkaProperties,
            SchedulerAppProperties appProperties) {

        Map<String, Object> props = kafkaProperties.buildConsumerProperties(null);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, appProperties.getKafka().getConsumer().getGroupId());

        JsonDeserializer<UserIdForProcessingCommand> valueDeserializer =
                new JsonDeserializer<>(UserIdForProcessingCommand.class, false);
        valueDeserializer.addTrustedPackages(UserIdForProcessingCommand.class.getPackageName());

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    /**
     * Создает и настраивает фабрику контейнеров для batch listeners.
     */
    @Bean("batchKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, UserIdForProcessingCommand> batchKafkaListenerContainerFactory(
            ConsumerFactory<String, UserIdForProcessingCommand> batchConsumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate,
            SchedulerAppProperties properties) {

        ConcurrentKafkaListenerContainerFactory<String, UserIdForProcessingCommand> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(batchConsumerFactory);

        factory.setBatchListener(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        factory.setConcurrency(properties.getKafka().getConsumer().getConcurrency());

        SchedulerAppProperties.RetryAndDltProperties retryProps = properties.getKafka().getConsumer().getRetryAndDlt();
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