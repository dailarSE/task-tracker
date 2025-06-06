package com.example.tasktracker.backend.kafka.service;

import com.example.tasktracker.backend.config.AppConfig;
import com.example.tasktracker.backend.kafka.domain.entity.UndeliveredWelcomeEmail;
import com.example.tasktracker.backend.kafka.domain.repository.UndeliveredWelcomeEmailRepository;
import com.example.tasktracker.backend.user.messaging.dto.EmailTriggerCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для {@link EmailNotificationOrchestratorService}.
 */
@ExtendWith(MockitoExtension.class)
class EmailNotificationOrchestratorServiceTest {

    @Mock
    private KafkaTemplate<String, EmailTriggerCommand> mockKafkaTemplate;
    @Mock
    private UndeliveredWelcomeEmailRepository mockUndeliveredCommandRepository;
    @Mock
    private Clock mockClock;
    @Mock
    @Qualifier(AppConfig.KAFKA_ASYNC_OPERATIONS_EXECUTOR) // Такое же имя, как в AppConfig
    private Executor mockKafkaAsyncOperationsExecutor;
    @Mock
    private MeterRegistry mockMeterRegistry;
    @Mock
    private Counter mockCriticalDeliveryFailureCounter; // Мок для нашего счетчика

    // @Spy // Нельзя использовать @Spy и @InjectMocks одновременно так просто для self-инъекции
    // Вместо этого будем инжектировать мок self и настраивать его поведение.
    @Mock
    private EmailNotificationOrchestratorService mockSelf;


    private EmailNotificationOrchestratorService orchestratorService;

    @Captor
    private ArgumentCaptor<UndeliveredWelcomeEmail> undeliveredWelcomeEmailCaptor;


    private static final String TEST_EMAIL = "test@example.com";
    private static final Long TEST_USER_ID = 123L;
    private static final String TEST_TEMPLATE_ID = "USER_WELCOME";
    private static final String TEST_LOCALE = "en-US";
    private static final String TEST_CORRELATION_ID = UUID.randomUUID().toString();
    private static final Instant FIXED_NOW = Instant.parse("2025-01-01T12:00:00Z");
    private static final String TEST_TOPIC = "EMAIL_SENDING_TASKS_from_property";


    @BeforeEach
    void setUp() {
        // Настройка мока Clock
        lenient().when(mockClock.instant()).thenReturn(FIXED_NOW);
        lenient().when(mockClock.getZone()).thenReturn(ZoneId.of("UTC"));

        // Настройка мока Executor'а для немедленного выполнения коллбэков в том же потоке
        lenient().doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            r.run();
            return null;
        }).when(mockKafkaAsyncOperationsExecutor).execute(any(Runnable.class));

        // Создаем экземпляр сервиса вручную, передавая все моки, включая мок self
        orchestratorService = new EmailNotificationOrchestratorService(
                mockKafkaTemplate,
                mockUndeliveredCommandRepository,
                mockClock,
                mockKafkaAsyncOperationsExecutor, // Передаем мок executor'а
                TEST_TOPIC,
                mockMeterRegistry,
                mockSelf // Передаем мок self
        );
    }

    private EmailTriggerCommand createTestCommand() {
        Map<String, Object> context = new HashMap<>();
        context.put("userEmail", TEST_EMAIL);
        return new EmailTriggerCommand(
                TEST_EMAIL,
                TEST_TEMPLATE_ID,
                context,
                TEST_LOCALE,
                TEST_USER_ID,
                TEST_CORRELATION_ID // Предустановленный correlationId для простоты тестов ensureCorrelationIdIsSet
        );
    }

    @Nested
    @DisplayName("scheduleInitialEmailNotification Tests")
    class ScheduleInitialEmailNotificationTests {

        @Test
        @DisplayName("Успешная отправка в Kafka -> должен залогировать успех, не сохранять в fallback")
        void whenKafkaSendSucceeds_shouldLogSuccessAndNotSaveToFallback() {
            // Arrange
            EmailTriggerCommand command = createTestCommand();
            CompletableFuture<SendResult<String, EmailTriggerCommand>> successfulFuture =
                    CompletableFuture.completedFuture(mock(SendResult.class));
            when(mockKafkaTemplate.send(eq(TEST_TOPIC), eq(command.getRecipientEmail()), eq(command)))
                    .thenReturn(successfulFuture);

            // Act
            orchestratorService.scheduleInitialEmailNotification(command);

            // Assert
            verify(mockKafkaTemplate).send(TEST_TOPIC, command.getRecipientEmail(), command);
            verifyNoInteractions(mockUndeliveredCommandRepository); // Не должно быть вызовов к fallback репозиторию
            verifyNoInteractions(mockSelf); // self.persistNewUndeliveredCommand не должен вызываться
            verify(mockCriticalDeliveryFailureCounter, never()).increment();
        }

        @Test
        @DisplayName("Ошибка отправки в Kafka, УСПЕШНОЕ сохранение в fallback -> должен вызвать persistNewUndeliveredCommand")
        void whenKafkaSendFailsAndFallbackSaveSucceeds_shouldCallPersistAndLog() throws JsonProcessingException {
            // Arrange
            EmailTriggerCommand command = createTestCommand();
            RuntimeException kafkaException = new RuntimeException("Kafka send failed");
            CompletableFuture<SendResult<String, EmailTriggerCommand>> failedFuture =
                    CompletableFuture.failedFuture(kafkaException);

            when(mockKafkaTemplate.send(eq(TEST_TOPIC), eq(command.getRecipientEmail()), eq(command)))
                    .thenReturn(failedFuture);

            // Мокируем self.persistNewUndeliveredCommand, чтобы он ничего не делал (успешно)
            // Это важно, так как мы тестируем логику scheduleInitialEmailNotification, а не самого persist
            doNothing().when(mockSelf).persistNewUndeliveredCommand(
                    any(EmailTriggerCommand.class),
                    anyString()
            );

            // Act
            orchestratorService.scheduleInitialEmailNotification(command);

            // Assert
            verify(mockKafkaTemplate).send(TEST_TOPIC, command.getRecipientEmail(), command);
            // Проверяем, что был вызван метод сохранения в fallback через self-прокси
            verify(mockSelf).persistNewUndeliveredCommand(eq(command), eq(kafkaException.getMessage()));
            verify(mockCriticalDeliveryFailureCounter, never()).increment();
        }

        @Nested
        @DisplayName("persistNewUndeliveredCommand Tests")
        class PersistNewUndeliveredCommandTests {

            @Test
            @DisplayName("Корректный маппинг и сохранение в UndeliveredWelcomeEmail")
            void shouldCorrectlyMapAndSaveToUndeliveredWelcomeEmail() {
                // Arrange
                // Создаем тестовую команду, как и раньше
                EmailTriggerCommand command = createTestCommand();
                // Для этого теста userId должен быть Long, как в сущности
                command.setUserId(TEST_USER_ID); // Убедимся, что ID в команде корректен
                command.setCorrelationId("test-trace-id-123"); // Устанавливаем traceId

                String deliveryErrorMessage = "Kafka is on fire";

                // Мокируем репозиторий для новой сущности
                when(mockUndeliveredCommandRepository.save(any(UndeliveredWelcomeEmail.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0)); // Возвращаем то, что пришло на вход

                // Act
                // Вызываем тестируемый метод
                orchestratorService.persistNewUndeliveredCommand(command, deliveryErrorMessage);

                // Assert
                // Захватываем аргумент, переданный в метод save()
                verify(mockUndeliveredCommandRepository).save(undeliveredWelcomeEmailCaptor.capture());
                UndeliveredWelcomeEmail savedEntity = undeliveredWelcomeEmailCaptor.getValue();

                // Проверяем, что все поля смаппились правильно
                assertThat(savedEntity.getUserId()).isEqualTo(TEST_USER_ID); // Проверяем PK
                assertThat(savedEntity.getRecipientEmail()).isEqualTo(command.getRecipientEmail());
                assertThat(savedEntity.getLocale()).isEqualTo(command.getLocale());
                assertThat(savedEntity.getLastAttemptTraceId()).isEqualTo(command.getCorrelationId());
                assertThat(savedEntity.getDeliveryErrorMessage()).isEqualTo(deliveryErrorMessage);

                // Проверяем временные метки, которые устанавливаются вручную из mockClock
                Instant expectedTimestamp = FIXED_NOW.truncatedTo(ChronoUnit.MICROS);
                assertThat(savedEntity.getInitialAttemptAt()).isEqualTo(expectedTimestamp);
                assertThat(savedEntity.getLastAttemptAt()).isEqualTo(expectedTimestamp);

                // Проверяем поля со значениями по умолчанию
                assertThat(savedEntity.getRetryCount()).isZero();
            }

            @Test
            @DisplayName("Null command -> NullPointerException")
            void whenCommandIsNull_shouldThrowNPE() {
                assertThatNullPointerException().isThrownBy(() ->
                        orchestratorService.persistNewUndeliveredCommand(null, "error"));
            }
            // ... другие тесты на null для topic, deliveryErrorMessage
        }


        @Nested
        @DisplayName("ensureCorrelationIdIsSet Tests")
        class EnsureCorrelationIdIsSetTests {

            @Test
            @DisplayName("CorrelationId уже установлен в команде -> не должен меняться")
            void whenCorrelationIdIsAlreadySet_shouldNotChangeIt() {
                EmailTriggerCommand command = new EmailTriggerCommand();
                String existingCorrelationId = "existing-corr-id";
                command.setCorrelationId(existingCorrelationId);

                orchestratorService.ensureCorrelationIdIsSet(command);

                assertThat(command.getCorrelationId()).isEqualTo(existingCorrelationId);
            }

            @Test
            @DisplayName("CorrelationId null -> должен сгенерировать UUID (ветка с OTel TraceId не тестируется изолированно)")
            void whenCorrelationIdIsNull_shouldGenerateUuid() {
                // Этот тест проверяет ветку, когда OTel TraceId НЕ доступен
                // (мы не можем надежно замокать Span.current() в юнит-тесте без mockito-inline)
                EmailTriggerCommand command = new EmailTriggerCommand();
                command.setCorrelationId(null);

                // Act
                orchestratorService.ensureCorrelationIdIsSet(command);

                // Assert
                assertThat(command.getCorrelationId()).isNotNull().isNotBlank();
                // Проверяем, что это UUID-подобная строка
                assertThatCode(() -> UUID.fromString(command.getCorrelationId())).doesNotThrowAnyException();
            }

            @ParameterizedTest
            @ValueSource(strings = {"", " "})
            @DisplayName("CorrelationId пустой или blank -> должен сгенерировать UUID")
            void whenCorrelationIdIsBlank_shouldGenerateUuid(String blankCorrelationId) {
                EmailTriggerCommand command = new EmailTriggerCommand();
                command.setCorrelationId(blankCorrelationId);

                orchestratorService.ensureCorrelationIdIsSet(command);

                assertThat(command.getCorrelationId()).isNotNull().isNotBlank();
                assertThatCode(() -> UUID.fromString(command.getCorrelationId())).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("Передача null команды -> должен выбросить NullPointerException")
            void whenCommandIsNull_shouldThrowNPE() {
                assertThatNullPointerException()
                        .isThrownBy(() -> orchestratorService.ensureCorrelationIdIsSet(null));
            }
        }
    }
}