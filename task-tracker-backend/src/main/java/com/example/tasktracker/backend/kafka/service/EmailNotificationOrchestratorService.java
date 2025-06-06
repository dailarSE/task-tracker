package com.example.tasktracker.backend.kafka.service;

import com.example.tasktracker.backend.common.MdcKeys;
import com.example.tasktracker.backend.config.AppConfig;
import com.example.tasktracker.backend.kafka.domain.entity.UndeliveredWelcomeEmail;
import com.example.tasktracker.backend.kafka.domain.repository.UndeliveredWelcomeEmailRepository;
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
    private final UndeliveredWelcomeEmailRepository undeliveredCommandRepository;
    private final Clock clock;
    private final Executor kafkaAsyncOperationsExecutor;
    private final EmailNotificationOrchestratorService self;
    private final Counter criticalDeliveryFailureCounter;


    public final String emailCommandsTopicName;

    public EmailNotificationOrchestratorService(
            KafkaTemplate<String, EmailTriggerCommand> kafkaTemplate,
            UndeliveredWelcomeEmailRepository undeliveredCommandRepository,
            Clock clock,
            @Qualifier(AppConfig.KAFKA_ASYNC_OPERATIONS_EXECUTOR) Executor kafkaAsyncOperationsExecutor,
            @Value("${app.kafka.topic.email-commands.name}") String emailCommandsTopicName,
            MeterRegistry meterRegistry,
            @Lazy EmailNotificationOrchestratorService self) {
        this.kafkaTemplate = kafkaTemplate;
        this.undeliveredCommandRepository = undeliveredCommandRepository;
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
     * Асинхронно инициирует отправку команды на email-уведомление в Kafka.
     * Выполняется в потоке из пула {@link AppConfig#KAFKA_ASYNC_OPERATIONS_EXECUTOR}.
     * Обрабатывает результат отправки асинхронно, сохраняя команду в fallback-таблицу при неудаче.
     *
     * @param command DTO {@link EmailTriggerCommand} с данными для отправки.
     */
    @Async(AppConfig.KAFKA_ASYNC_OPERATIONS_EXECUTOR)
    public void scheduleInitialEmailNotification(@NonNull EmailTriggerCommand command) {
        final String userIdFromMdc = MDC.get(MdcKeys.USER_ID);
        ensureCorrelationIdIsSet(command);

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
                                handleInitialSendFailure(command, correlationId, exception);
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
            handleInitialSendFailure(command, correlationId, sendException);
        }
    }

    /**
     * Метод для обработки неуспешной ПЕРВИЧНОЙ отправки команды в Kafka.
     * Логирует ошибку и пытается сохранить команду в fallback-таблицу.
     * Вызывается как при синхронных, так и при асинхронных ошибках отправки.
     * Ожидается, что MDC (userId, correlationId) уже установлен вызывающим кодом.
     */
    void handleInitialSendFailure(@NonNull EmailTriggerCommand command,
                                  @NonNull String correlationId,
                                  @NonNull Throwable deliveryException) {
        log.error("Failed to send EmailTriggerCommand to Kafka. CorrelationId: {}, Recipient: {}, Cause: {}",
                correlationId, command.getRecipientEmail(), deliveryException.getMessage(), deliveryException);
        try {
            self.persistNewUndeliveredCommand(command, deliveryException.getMessage());
            log.info("Successfully saved undelivered EmailTriggerCommand to fallback table. CorrelationId: {}", correlationId);
        } catch (Exception saveEx) {
            log.error("CRITICAL FAILURE: Failed to send EmailTriggerCommand to Kafka AND failed to save it to fallback table. " +
                            "CorrelationId: {}, Recipient: {}. Kafka Error: {}. DB Save Error: {}",
                    correlationId, command.getRecipientEmail(),
                    deliveryException.getMessage(), saveEx.getMessage(), saveEx);
            criticalDeliveryFailureCounter.increment();
        }
    }

    /**
     * Сохраняет информацию о первоначально неотправленной команде в таблицу {@code undelivered_email_commands}.
     * Выполняется в НОВОЙ ТРАНЗАКЦИИ.
     *
     * @param originalCommand      Исходная команда {@link EmailTriggerCommand}.
     * @param deliveryErrorMessage Сообщение об ошибке от Kafka или механизма отправки.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void persistNewUndeliveredCommand(@NonNull EmailTriggerCommand originalCommand,
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

        UndeliveredWelcomeEmail saved = undeliveredCommandRepository.save(undelivered);
        log.debug("Saved UndeliveredEmailCommand with ID: {} for CorrelationId: {}",
                saved.getUserId(), saved.getLastAttemptTraceId());
    }

    /**
     * Гарантирует, что у команды установлен {@code correlationId}.
     * Если {@code command.getCorrelationId()} пуст или null, пытается установить его
     * из текущего OpenTelemetry Trace ID. Если Trace ID невалиден, генерирует UUID.
     *
     * @param command Команда, для которой нужно установить {@code correlationId}.
     */
    void ensureCorrelationIdIsSet(@NonNull EmailTriggerCommand command) {
        if (command.getCorrelationId() == null || command.getCorrelationId().isBlank()) {
            String traceId = Span.current().getSpanContext().getTraceId();
            if (Span.current().getSpanContext().isValid()) {
                command.setCorrelationId(traceId);
                log.trace("Set correlationId for EmailTriggerCommand from OTel TraceId: {}", traceId);
            } else {
                String newUuid = UUID.randomUUID().toString();
                command.setCorrelationId(newUuid);
                log.warn("OTel TraceId not available or invalid. Generated new UUID for correlationId: {}", newUuid);
            }
        }
    }

    //TODO: ретраи шедулером.  (update + delete)
}