package com.example.tasktracker.backend.config;

import com.example.tasktracker.backend.kafka.config.KafkaConfig;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Компонент, отвечающий за проверку существования необходимых Kafka-топиков
 * при старте приложения.
 * <p>
 * Реализует {@link SmartInitializingSingleton}, что гарантирует выполнение проверки
 * после инициализации всех синглтон-бинов, но до полного завершения старта
 * приложения.
 * </p>
 * <p>
 * Если какой-либо из ожидаемых топиков не найден в Kafka, или если происходит
 * ошибка при взаимодействии с Kafka (например, брокер недоступен),
 * верификатор логирует критическую ошибку и выбрасывает {@link IllegalStateException}.
 * Это приводит к прерыванию процесса запуска приложения (fail-fast), предотвращая
 * запуск сервиса в неработоспособном состоянии из-за отсутствия критически
 * важной инфраструктуры обмена сообщениями.
 * </p>
 * <p>
 * Предполагается, что сами топики создаются и конфигурируются отдельно.
 * Бин {@link NewTopic} в {@link KafkaConfig} используется этим верификатором
 * в основном для получения имени ожидаемого топика.
 * </p>
 *
 * @see KafkaConfig Конфигурация, где объявляются бины NewTopic и KafkaAdmin.
 * @see SmartInitializingSingleton
 * @see KafkaAdmin
 * @see NewTopic
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.kafka.topic-verifier.enabled", havingValue = "true")
public class KafkaTopicVerifier implements SmartInitializingSingleton {

    @NonNull
    private final KafkaAdmin kafkaAdmin;
    @NonNull
    private final List<NewTopic> declaredTopics;

    /**
     * Выполняется после инициализации всех синглтон-бинов.
     * Проверяет существование всех Kafka-топиков, определенных как бины {@link NewTopic}.
     *
     * @throws IllegalStateException если один из ожидаемых топиков не найден
     *                               или произошла ошибка при связи с Kafka.
     */
    @Override
    public void afterSingletonsInstantiated() {
        if (this.declaredTopics.isEmpty()) {
            log.debug("No NewTopic beans declared in the application context. Skipping Kafka topic verification.");
            return;
        }

        List<String> expectedTopicNamesList = this.declaredTopics.stream()
                .map(NewTopic::name)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();

        if (expectedTopicNamesList.isEmpty()) {
            log.warn("No valid topic names derived from NewTopic beans. " +
                    "Skipping Kafka topic verification. Check NewTopic bean definitions and configuration properties.");
            return;
        }

        String[] expectedTopicNamesArray = expectedTopicNamesList.toArray(new String[0]);

        log.debug("Verifying existence of {} declared Kafka topic(s): [{}] during application initialization...",
                expectedTopicNamesArray.length, String.join(", ", expectedTopicNamesArray));

        try {
            Map<String, TopicDescription> existingTopicDescriptions = kafkaAdmin.describeTopics(expectedTopicNamesArray);

            for (String expectedTopicName : expectedTopicNamesArray) {
                if (!existingTopicDescriptions.containsKey(expectedTopicName)) {
                    String errorMessage = String.format(
                            "CRITICAL KAFKA CONFIGURATION ERROR: Declared Kafka topic '%s' was NOT FOUND on the broker. " +
                                    "The application requires this topic to function correctly and cannot complete startup.",
                            expectedTopicName);
                    log.error(errorMessage);
                    throw new IllegalStateException(errorMessage);
                }
                log.debug("Successfully verified existence of Kafka topic: {}", expectedTopicName);
            }
            log.info("All {} declared Kafka topics successfully verified by name: [{}].",
                    expectedTopicNamesArray.length, String.join(", ", expectedTopicNamesArray));

        } catch (Exception e) {
            // Этот блок перехватит таймауты от KafkaAdmin, ошибки подключения,
            // или если describeTopics() вернул ошибку по другой причине.
            String errorMessage = String.format(
                    "CRITICAL KAFKA CONNECTION/VERIFICATION ERROR: Failed to verify declared Kafka topics (%s) during startup. " +
                            "Ensure Kafka brokers are running, accessible, and all declared topics exist. " +
                            "Underlying cause: %s - %s",
                    String.join(", ", expectedTopicNamesArray), e.getClass().getSimpleName(), e.getMessage());
            log.error(errorMessage, e);
            throw new IllegalStateException(errorMessage, e);
        }
    }
}