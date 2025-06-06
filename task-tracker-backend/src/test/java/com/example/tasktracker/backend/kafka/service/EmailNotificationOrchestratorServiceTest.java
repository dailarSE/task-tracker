package com.example.tasktracker.backend.kafka.service;

import com.example.tasktracker.backend.kafka.domain.entity.UndeliveredWelcomeEmail;
import com.example.tasktracker.backend.kafka.domain.repository.UndeliveredWelcomeEmailRepository;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.messaging.dto.EmailTriggerCommand;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для {@link EmailNotificationOrchestratorService}.
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationOrchestratorServiceTest {

    @Mock
    private KafkaTemplate<String, EmailTriggerCommand> mockKafkaTemplate;
    @Mock
    private UndeliveredWelcomeEmailRepository mockUndeliveredWelcomeEmailRepository;
    @Mock
    private Clock mockClock;
    @Mock
    private Executor mockKafkaAsyncOperationsExecutor;
    @Mock
    private MeterRegistry mockMeterRegistry;
    @Mock
    private Counter mockCriticalDeliveryFailureCounter;
    @Mock
    private EmailNotificationOrchestratorService mockSelf;

    private EmailNotificationOrchestratorService orchestratorService;

    @Captor
    private ArgumentCaptor<EmailTriggerCommand> emailCommandCaptor;
    @Captor
    private ArgumentCaptor<UndeliveredWelcomeEmail> undeliveredWelcomeEmailCaptor;

    private static final String TEST_EMAIL = "test@example.com";
    private static final Long TEST_USER_ID = 123L;
    private static final String TEST_LOCALE_TAG = "en-US";
    private static final Instant FIXED_NOW = Instant.parse("2025-01-01T12:00:00Z");
    private static final String TEST_TOPIC_NAME = "EMAIL_SENDING_TASKS_from_property";

    private User testUser;

    @BeforeEach
    void setUp() {
        lenient().when(mockClock.instant()).thenReturn(FIXED_NOW);
        lenient().when(mockClock.getZone()).thenReturn(ZoneId.of("UTC"));

        lenient().doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            r.run();
            return null;
        }).when(mockKafkaAsyncOperationsExecutor).execute(any(Runnable.class));

        orchestratorService = new EmailNotificationOrchestratorService(
                mockKafkaTemplate,
                mockUndeliveredWelcomeEmailRepository,
                mockClock,
                mockKafkaAsyncOperationsExecutor,
                TEST_TOPIC_NAME,
                mockMeterRegistry,
                mockSelf
        );

        // Создаем тестового пользователя
        testUser = new User();
        testUser.setId(TEST_USER_ID);
        testUser.setEmail(TEST_EMAIL);
    }

    @Nested
    @DisplayName("scheduleInitialEmailNotification Tests")
    class ScheduleInitialEmailNotificationTests {

        @Test
        @DisplayName("Успешная отправка в Kafka -> должен вызвать send с корректной командой")
        void whenKafkaSendSucceeds_shouldCallSendAndSucceed() {
            // Arrange
            CompletableFuture<SendResult<String, EmailTriggerCommand>> successfulFuture =
                    CompletableFuture.completedFuture(mock(SendResult.class));
            when(mockKafkaTemplate.send(anyString(), anyString(), any(EmailTriggerCommand.class)))
                    .thenReturn(successfulFuture);

            // Act
            orchestratorService.scheduleInitialEmailNotification(testUser, TEST_LOCALE_TAG);

            // Assert
            verify(mockKafkaTemplate).send(eq(TEST_TOPIC_NAME), eq(testUser.getEmail()), emailCommandCaptor.capture());
            EmailTriggerCommand capturedCommand = emailCommandCaptor.getValue();

            // Проверяем, что команда была создана правильно
            assertThat(capturedCommand.getRecipientEmail()).isEqualTo(TEST_EMAIL);
            assertThat(capturedCommand.getTemplateId()).isEqualTo("USER_WELCOME");
            assertThat(capturedCommand.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(capturedCommand.getLocale()).isEqualTo(TEST_LOCALE_TAG);
            assertThat(capturedCommand.getCorrelationId()).isNotNull(); // Проверяем, что correlationId был сгенерирован

            // Проверяем, что fallback-логика не вызывалась
            verify(mockSelf, never()).persistNewUndeliveredWelcomeEmail(any(), any());
            verify(mockCriticalDeliveryFailureCounter, never()).increment();
        }

        @Test
        @DisplayName("Синхронная ошибка Kafka -> должен вызвать handleInitialSendFailure")
        void whenKafkaSendThrowsSyncException_shouldCallHandleFailure() {
            // Arrange
            RuntimeException syncKafkaException = new RuntimeException("Synchronous Kafka send failed");
            when(mockKafkaTemplate.send(anyString(), anyString(), any(EmailTriggerCommand.class)))
                    .thenThrow(syncKafkaException);

            // Используем spy, чтобы проверить вызов приватного метода
            EmailNotificationOrchestratorService spyOrchestrator = spy(orchestratorService);
            doNothing().when(spyOrchestrator).handleInitialSendFailure(any(), any());

            // Act
            spyOrchestrator.scheduleInitialEmailNotification(testUser, TEST_LOCALE_TAG);

            // Assert
            verify(spyOrchestrator).handleInitialSendFailure(any(EmailTriggerCommand.class), eq(syncKafkaException));
        }

        @Test
        @DisplayName("Асинхронная ошибка Kafka -> должен вызвать handleInitialSendFailure")
        void whenKafkaSendFailsAsync_shouldCallHandleFailure() {
            // Arrange
            RuntimeException asyncKafkaException = new RuntimeException("Async Kafka send failed");
            CompletableFuture<SendResult<String, EmailTriggerCommand>> failedFuture =
                    CompletableFuture.failedFuture(asyncKafkaException);
            when(mockKafkaTemplate.send(anyString(), anyString(), any(EmailTriggerCommand.class)))
                    .thenReturn(failedFuture);

            EmailNotificationOrchestratorService spyOrchestrator = spy(orchestratorService);
            doNothing().when(spyOrchestrator).handleInitialSendFailure(any(), any());

            // Act
            spyOrchestrator.scheduleInitialEmailNotification(testUser, TEST_LOCALE_TAG);

            // Assert
            verify(spyOrchestrator).handleInitialSendFailure(any(EmailTriggerCommand.class), eq(asyncKafkaException));
        }
    }

    @Nested
    @DisplayName("handleInitialSendFailure Tests")
    class HandleInitialSendFailureTests {

        @Test
        @DisplayName("Сбой Kafka, Успешное сохранение в fallback -> должен вызвать persist")
        void whenKafkaFailsAndFallbackSucceeds_shouldCallPersist() throws Exception {
            // Arrange
            EmailTriggerCommand command = orchestratorService.createEmailTriggerCommand(testUser, TEST_LOCALE_TAG);
            RuntimeException deliveryException = new RuntimeException("Delivery failed");
            doNothing().when(mockSelf).persistNewUndeliveredWelcomeEmail(any(), any());

            // Act
            orchestratorService.handleInitialSendFailure(command, deliveryException);

            // Assert
            verify(mockSelf).persistNewUndeliveredWelcomeEmail(command, deliveryException.getMessage());
            verify(mockCriticalDeliveryFailureCounter, never()).increment();
        }
    }

    @Nested
    @DisplayName("persistNewUndeliveredWelcomeEmail Tests")
    class PersistNewUndeliveredWelcomeEmailTests {

        @Test
        @DisplayName("Корректный маппинг и сохранение")
        void shouldCorrectlyMapAndSaveUndeliveredWelcomeEmail() {
            // Arrange
            EmailTriggerCommand command = orchestratorService.createEmailTriggerCommand(testUser, TEST_LOCALE_TAG);
            String deliveryErrorMessage = "Kafka is on fire";

            when(mockUndeliveredWelcomeEmailRepository.save(any(UndeliveredWelcomeEmail.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Act
            orchestratorService.persistNewUndeliveredWelcomeEmail(command, deliveryErrorMessage);

            // Assert
            verify(mockUndeliveredWelcomeEmailRepository).save(undeliveredWelcomeEmailCaptor.capture());
            UndeliveredWelcomeEmail savedEntity = undeliveredWelcomeEmailCaptor.getValue();

            assertThat(savedEntity.getUserId()).isEqualTo(testUser.getId());
            assertThat(savedEntity.getRecipientEmail()).isEqualTo(testUser.getEmail());
            assertThat(savedEntity.getLocale()).isEqualTo(TEST_LOCALE_TAG);
            assertThat(savedEntity.getLastAttemptTraceId()).isEqualTo(command.getCorrelationId());
            assertThat(savedEntity.getDeliveryErrorMessage()).isEqualTo(deliveryErrorMessage);

            Instant expectedTimestamp = FIXED_NOW.truncatedTo(ChronoUnit.MICROS);
            assertThat(savedEntity.getInitialAttemptAt()).isEqualTo(expectedTimestamp);
            assertThat(savedEntity.getLastAttemptAt()).isEqualTo(expectedTimestamp);
            assertThat(savedEntity.getRetryCount()).isZero();
        }

        @Test
        @DisplayName("Null command -> должен выбросить NullPointerException")
        void whenCommandIsNull_shouldThrowNPE() {
            assertThatNullPointerException().isThrownBy(() ->
                    orchestratorService.persistNewUndeliveredWelcomeEmail(null, "error"));
        }
    }

    @Nested
    @DisplayName("createEmailTriggerCommand / generateCorrelationId Tests")
    class CreateCommandTests {
        @Test
        @DisplayName("Создание команды -> должен установить все поля корректно")
        void createEmailTriggerCommand_shouldSetAllFieldsCorrectly() {
            // Act
            EmailTriggerCommand command = orchestratorService.createEmailTriggerCommand(testUser, TEST_LOCALE_TAG);

            // Assert
            assertThat(command.getRecipientEmail()).isEqualTo(TEST_EMAIL);
            assertThat(command.getTemplateId()).isEqualTo("USER_WELCOME");
            assertThat(command.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(command.getLocale()).isEqualTo(TEST_LOCALE_TAG);
            assertThat(command.getTemplateContext()).containsEntry("userEmail", TEST_EMAIL);

            // Проверяем, что generateCorrelationId сработал
            assertThat(command.getCorrelationId()).isNotNull();
            assertThatCode(() -> UUID.fromString(command.getCorrelationId())).doesNotThrowAnyException();
        }
    }
}