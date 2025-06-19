package com.example.tasktracker.backend.kafka.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Map;

/**
 * Конфигурационный класс для Apache Kafka.
 * Определяет бины {@link KafkaAdmin} (для администрирования) и {@link NewTopic}
 * (для декларации ожидаемых топиков). Имена топиков и параметры подключения
 * управляются через {@code application.yml}.
 * <p>
 * {@link KafkaAdmin} настроен на явное отключение авто-создания топиков
 * и на быстрый отказ при недоступности брокера.
 * Бины {@link NewTopic} используются {@link KafkaTopicVerifier} для валидации
 * существования топиков при старте приложения.
 * </p>
 * @see KafkaTopicVerifier
 * @see KafkaProperties
 */
@Configuration
@Slf4j
public class KafkaConfig {

    /** Имя Kafka-топика для команд на отправку email. Инжектируется из {@code app.kafka.topic.email-commands.name}. */
    @Value("${app.kafka.topic.email-commands.name}")
    private String emailCommandsTopicName;

    /** Таймаут для операций KafkaAdmin (сек). Инжектируется из {@code app.kafka.admin.operation-timeout-seconds}. */
    @Value("${app.kafka.admin.operation-timeout-seconds:11}")
    private int kafkaAdminOperationTimeoutSeconds;

    /** Таймаут на запрос к Kafka AdminClient (мс). Инжектируется из {@code app.kafka.admin.request-timeout-ms}. */
    @Value("${app.kafka.admin.request-timeout-ms:5000}")
    private int kafkaAdminRequestTimeoutMs;

    /** Общий таймаут для API AdminClient (мс). Инжектируется из {@code app.kafka.admin.default-api-timeout-ms}. */
    @Value("${app.kafka.admin.default-api-timeout-ms:10000}")
    private int kafkaAdminDefaultApiTimeoutMs;

    /**
     * Декларирует ожидаемый топик Kafka для команд на отправку email.
     * Используется {@link KafkaTopicVerifier} для проверки его существования по имени.
     * Конфигурация партиций/реплик не задается, так как управляется внешне.
     *
     * @return Бин {@link NewTopic}.
     */
    @Bean
    public NewTopic emailCommandsExpectedTopicName() {
        return TopicBuilder.name(this.emailCommandsTopicName).build();
    }

    /**
     * Конфигурирует и предоставляет бин {@link KafkaAdmin}.
     * {@link KafkaAdmin} используется {@link KafkaTopicVerifier} для проверки
     * существования топиков. Настроен на отказ при недоступности брокера и
     * явное отключение авто-создания топиков приложением.
     * Таймауты для административных операций берутся из свойств приложения.
     *
     * @param kafkaProperties Автоконфигурированные свойства Kafka.
     * @return Сконфигурированный бин {@link KafkaAdmin}.
     */
    @Bean
    public KafkaAdmin kafkaAdmin(KafkaProperties kafkaProperties) {
        Map<String, Object> adminClientProps = kafkaProperties.buildAdminProperties(null);

        adminClientProps.putIfAbsent(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, Integer.toString(kafkaAdminRequestTimeoutMs));
        adminClientProps.putIfAbsent(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, Integer.toString(kafkaAdminDefaultApiTimeoutMs));

        KafkaAdmin kafkaAdmin = new KafkaAdmin(adminClientProps);
        kafkaAdmin.setAutoCreate(false);
        kafkaAdmin.setFatalIfBrokerNotAvailable(true);
        kafkaAdmin.setOperationTimeout(kafkaAdminOperationTimeoutSeconds);

        log.debug("Configured KafkaAdmin with autoCreate=false, fatalIfBrokerNotAvailable=true, operationTimeout={}s. " +
                        "RequestTimeoutMs: {}ms, DefaultApiTimeoutMs: {}ms. Bootstrap servers: {}",
                kafkaAdminOperationTimeoutSeconds,
                kafkaAdminRequestTimeoutMs,
                kafkaAdminDefaultApiTimeoutMs,
                kafkaProperties.getBootstrapServers());

        return kafkaAdmin;
    }
}