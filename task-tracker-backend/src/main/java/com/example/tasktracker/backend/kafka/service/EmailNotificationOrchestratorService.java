package com.example.tasktracker.backend.kafka.service;

import com.example.tasktracker.backend.common.MdcKeys;
import com.example.tasktracker.backend.config.AppConfig;
import com.example.tasktracker.backend.kafka.domain.entity.UndeliveredWelcomeEmail;
import com.example.tasktracker.backend.kafka.domain.repository.UndeliveredWelcomeEmailRepository;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.messaging.dto.EmailTriggerCommand;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Сервис-оркестратор для управления отправкой email-уведомлений через Kafka.
 * Отвечает за инициирование отправки, обработку результатов (успех/ошибка)
 * и сохранение информации о неотправленных командах в fallback-таблицу.
 */
@Service
@Slf4j
public class EmailNotificationOrchestratorService {
    private final KafkaTemplate<String, EmailTriggerCommand> kafkaTemplate;
    private final UndeliveredWelcomeEmailRepository undeliveredWelcomeEmailRepository;
    private final Clock clock;
    private final Executor kafkaAsyncOperationsExecutor;
    private final EmailNotificationOrchestratorService self;
    private final Counter criticalDeliveryFailureCounter;

    /**
     * Имя Kafka-топика для отправки команд на генерацию email.
     */
    public final String emailCommandsTopicName;

    public EmailNotificationOrchestratorService(
            KafkaTemplate<String, EmailTriggerCommand> kafkaTemplate,
            UndeliveredWelcomeEmailRepository undeliveredWelcomeEmailRepository,
            Clock clock,
            @Qualifier(AppConfig.KAFKA_ASYNC_OPERATIONS_EXECUTOR) Executor kafkaAsyncOperationsExecutor,
            @Value("${app.kafka.topic.email-commands.name}") String emailCommandsTopicName,
            MeterRegistry meterRegistry,
            @Lazy EmailNotificationOrchestratorService self) {
        this.kafkaTemplate = kafkaTemplate;
        this.undeliveredWelcomeEmailRepository = undeliveredWelcomeEmailRepository;
        this.clock = clock;
        this.kafkaAsyncOperationsExecutor = kafkaAsyncOperationsExecutor;
        this.self = self;
        this.emailCommandsTopicName = emailCommandsTopicName;
        // Инициализируем счетчик метрики
        this.criticalDeliveryFailureCounter = Counter
                .builder("tasktracker.kafka.email_command.delivery.critical_failure")
                .description("Counts critical failures for email command delivery (Kafka and DB fallback failed).")
                .tag("topic", emailCommandsTopicName)
                .register(meterRegistry);

    }

    /**
     * Асинхронно инициирует отправку приветственного email-уведомления для нового пользователя.
     * <p>
     * Метод создает {@link EmailTriggerCommand}, устанавливает для него ID корреляции
     * и запускает асинхронную отправку в Kafka. Выполняется в потоке из пула
     * {@link AppConfig#KAFKA_ASYNC_OPERATIONS_EXECUTOR}.
     * </p>
     * <p>
     * Результат отправки обрабатывается в асинхронном коллбэке, который в случае
     * сбоя сохраняет команду в fallback-таблицу.
     * </p>
     *
     * @param userToNotify          Сущность {@link User}, для которой отправляется уведомление.
     * @param notificationLocaleTag Языковой тег локали (например, "en-US") для письма.
     */
    @Async(AppConfig.KAFKA_ASYNC_OPERATIONS_EXECUTOR)
    public void scheduleInitialEmailNotification(@NonNull User userToNotify, String notificationLocaleTag) {
        final String userIdFromMdc = MDC.get(MdcKeys.USER_ID);

        EmailTriggerCommand command = createEmailTriggerCommand(userToNotify, notificationLocaleTag);
        String key = command.getRecipientEmail();
        String correlationId = command.getCorrelationId();

        log.info("Attempting to send EmailTriggerCommand to Kafka topic [{}]. CorrelationId: {}, Recipient: {}, TemplateId: {}",
                emailCommandsTopicName, correlationId, command.getRecipientEmail(), command.getTemplateId());

        try {
            CompletableFuture<SendResult<String, EmailTriggerCommand>> future =
                    kafkaTemplate.send(emailCommandsTopicName, key, command);

            future.whenCompleteAsync((sendResult, exception) -> {
                        try (MDC.MDCCloseable ignored = MDC.putCloseable(MdcKeys.USER_ID, userIdFromMdc)) {
                            if (exception != null) {
                                log.error("Failed to send EmailTriggerCommand to Kafka. CorrelationId: {}, Topic: {}, Recipient: {}, Cause: {}",
                                        correlationId, emailCommandsTopicName, command.getRecipientEmail(), exception.getMessage(), exception);
                                handleInitialSendFailure(command, exception);
                            } else {
                                log.info("Successfully sent EmailTriggerCommand to Kafka. CorrelationId: {}, Topic: {}, Partition: {}, Offset: {}, Recipient: {}",
                                        correlationId,
                                        sendResult.getRecordMetadata().topic(),
                                        sendResult.getRecordMetadata().partition(),
                                        sendResult.getRecordMetadata().offset(),
                                        command.getRecipientEmail());
                            }
                        }
                    },
                    kafkaAsyncOperationsExecutor
            );
        } catch (Exception sendException) {
            handleInitialSendFailure(command, sendException);
        }
    }

    /**
     * Обрабатывает сбой первоначальной отправки команды в Kafka.
     * Логирует ошибку и инициирует сохранение команды в fallback-таблицу.
     *
     * @param command           Неотправленная команда.
     * @param deliveryException Исключение, вызвавшее сбой доставки.
     */
    void handleInitialSendFailure(@NonNull EmailTriggerCommand command,
                                  @NonNull Throwable deliveryException) {
        log.error("Failed to send EmailTriggerCommand to Kafka. CorrelationId: {}, Recipient: {}, Cause: {}",
                command.getCorrelationId(), command.getRecipientEmail(), deliveryException.getMessage(), deliveryException);
        try {
            self.persistNewUndeliveredWelcomeEmail(command, deliveryException.getMessage());
            log.info("Successfully saved undelivered EmailTriggerCommand to fallback table. CorrelationId: {}", command.getCorrelationId());
        } catch (Exception saveEx) {
            log.error("CRITICAL FAILURE: Failed to send EmailTriggerCommand to Kafka AND failed to save it to fallback table. " +
                            "CorrelationId: {}, Recipient: {}. Kafka Error: {}. DB Save Error: {}",
                    command.getCorrelationId(), command.getRecipientEmail(),
                    deliveryException.getMessage(), saveEx.getMessage(), saveEx);
            criticalDeliveryFailureCounter.increment();
        }
    }

    /**
     * Создает DTO {@link EmailTriggerCommand} для приветственного email-уведомления.
     *
     * @param newUser   Сущность пользователя.
     * @param localeTag Языковой тег для локализации письма.
     * @return Сконфигурированный объект {@link EmailTriggerCommand}.
     */
    EmailTriggerCommand createEmailTriggerCommand(@NonNull User newUser, String localeTag) {
        return new EmailTriggerCommand(
                newUser.getEmail(),
                "USER_WELCOME",
                Map.of("userEmail", newUser.getEmail()),
                localeTag,
                newUser.getId(),
                generateCorrelationId()
        );
    }

    /**
     * Сохраняет информацию о неотправленном приветственном письме в БД.
     * Выполняется в новой, независимой транзакции.
     *
     * @param originalCommand      Исходная команда.
     * @param deliveryErrorMessage Сообщение об ошибке доставки.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void persistNewUndeliveredWelcomeEmail(@NonNull EmailTriggerCommand originalCommand,
                                                     @NonNull String deliveryErrorMessage) {
        log.debug("Persisting initially failed Kafka command. CorrelationId: {}, Recipient: {}",
                originalCommand.getCorrelationId(), originalCommand.getRecipientEmail());

        UndeliveredWelcomeEmail undelivered = new UndeliveredWelcomeEmail();
        undelivered.setUserId(originalCommand.getUserId());
        undelivered.setRecipientEmail(originalCommand.getRecipientEmail());
        undelivered.setLocale(originalCommand.getLocale());
        undelivered.setLastAttemptTraceId(originalCommand.getCorrelationId());

        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        undelivered.setInitialAttemptAt(now);
        undelivered.setLastAttemptAt(now);

        undelivered.setRetryCount(0); // Первичная запись
        undelivered.setDeliveryErrorMessage(deliveryErrorMessage);

        UndeliveredWelcomeEmail saved = undeliveredWelcomeEmailRepository.save(undelivered);
        log.debug("Saved UndeliveredEmailCommand with ID: {} for CorrelationId: {}",
                saved.getUserId(), saved.getLastAttemptTraceId());
    }

    /**
     * Генерирует ID корреляции, используя OTel Trace ID если он доступен,
     * иначе генерирует новый UUID.
     *
     * @return Строка с ID корреляции.
     */
    String generateCorrelationId() {
        String traceId = Span.current().getSpanContext().getTraceId();
        if (Span.current().getSpanContext().isValid()) {
            log.trace("Set correlationId for EmailTriggerCommand from OTel TraceId: {}", traceId);
            return traceId;
        } else {
            String newUuid = UUID.randomUUID().toString();
            log.warn("OTel TraceId not available or invalid. Generated new UUID for correlationId: {}", newUuid);
            return newUuid;
        }
    }

    //TODO: ретраи шедулером.  (update + delete)
}