package com.example.tasktracker.scheduler.job;

import com.example.tasktracker.scheduler.job.dto.CursorPayload;
import com.example.tasktracker.scheduler.job.dto.JobExecutionState;
import com.example.tasktracker.scheduler.metrics.Metric;
import com.example.tasktracker.scheduler.metrics.MetricsReporter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Юнит-тесты для RedisJobStateRepository")
class RedisJobStateRepositoryTest {

    @Mock
    private StringRedisTemplate mockRedisTemplate;
    @Mock
    private HashOperations<String, String, String> mockHashOperations;
    @Mock
    private JobStateKeyProvider mockKeyProvider;
    @Mock
    private MetricsReporter mockMetrics;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RedisJobStateRepository repository;

    private static final String JOB_NAME = "daily-task-report";
    private static final LocalDate TEST_DATE = LocalDate.of(2025, 1, 15);
    private static final String HASH_KEY = "job_state:daily-task-report";
    private static final String HASH_FIELD = "2025-01-15";

    private final TypeReference<JobExecutionState<CursorPayload>> cursorPayloadTypeRef = new TypeReference<>() {};

    @BeforeEach
    void setUp() {
        // Настраиваем моки для генерации ключей, чтобы тесты не зависеli от реализации KeyProvider
        when(mockKeyProvider.getHashKey(JOB_NAME)).thenReturn(HASH_KEY);
        when(mockKeyProvider.getHashField(TEST_DATE)).thenReturn(HASH_FIELD);
        // opsForHash() должен возвращать наш мок
        lenient().when(mockRedisTemplate.opsForHash()).thenReturn((HashOperations) mockHashOperations);
    }

    @Nested
    @DisplayName("Метод findState()")
    class FindStateTests {

        @Test
        @DisplayName("TC R-01: Успешное чтение существующего состояния")
        void findState_whenStateExists_shouldReturnOptionalOfState() throws JsonProcessingException {
            // Arrange
            JobExecutionState<CursorPayload> expectedState = JobExecutionState.inProgress(new CursorPayload("abc"));
            String jsonState = objectMapper.writeValueAsString(expectedState);
            when(mockHashOperations.get(HASH_KEY, HASH_FIELD)).thenReturn(jsonState);

            // Act
            Optional<JobExecutionState<CursorPayload>> result = repository.findState(JOB_NAME, TEST_DATE, cursorPayloadTypeRef);

            // Assert
            assertThat(result).isPresent().contains(expectedState);
            verifyNoInteractions(mockMetrics);
        }

        @Test
        @DisplayName("TC R-02: Чтение отсутствующего состояния")
        void findState_whenStateDoesNotExist_shouldReturnEmptyOptional() {
            // Arrange
            when(mockHashOperations.get(HASH_KEY, HASH_FIELD)).thenReturn(null);

            // Act
            Optional<JobExecutionState<CursorPayload>> result = repository.findState(JOB_NAME, TEST_DATE, cursorPayloadTypeRef);

            // Assert
            assertThat(result).isEmpty();
            verifyNoInteractions(mockMetrics);
        }

        @Test
        @DisplayName("TC R-05: Обработка ошибки десериализации (битые данные)")
        void findState_whenJsonIsInvalid_shouldReturnEmptyAndIncrementMetric() {
            // Arrange
            String invalidJson = "{ \"status\": \"IN_PROGRESS\" ...";
            when(mockHashOperations.get(HASH_KEY, HASH_FIELD)).thenReturn(invalidJson);

            // Act
            Optional<JobExecutionState<CursorPayload>> result = repository.findState(JOB_NAME, TEST_DATE, cursorPayloadTypeRef);

            // Assert
            assertThat(result).isEmpty();
            verify(mockMetrics).incrementCounter(Metric.JOB_STATE_DESERIALIZATION_ERROR, Tags.of("job_name", JOB_NAME));
        }

        @Test
        @DisplayName("TC R-07: Обработка ошибки подключения к Redis")
        void findState_whenRedisIsUnavailable_shouldThrowRuntimeException() {
            // Arrange
            when(mockHashOperations.get(anyString(), anyString()))
                    .thenThrow(new RedisConnectionFailureException("Connection lost"));

            // Act & Assert
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> repository.findState(JOB_NAME, TEST_DATE, cursorPayloadTypeRef))
                    .withMessage("Could not read job state from Redis");
            verifyNoInteractions(mockMetrics); // Ошибка подключения, а не данных
        }

        @Test
        @DisplayName("TC R-08: Миграция со старой схемы (v0) -> должен прочитать и инкрементировать метрику")
        void findState_whenOldSchema_shouldMigrateAndIncrementMetric() {
            // Arrange
            // Старый JSON без поля schemaVersion
            String oldJson = "{\"status\":\"IN_PROGRESS\",\"errorMessage\":null,\"payload\":{\"lastCursor\":\"abc\"}}";
            when(mockHashOperations.get(HASH_KEY, HASH_FIELD)).thenReturn(oldJson);

            // Act
            Optional<JobExecutionState<CursorPayload>> result = repository.findState(JOB_NAME, TEST_DATE, cursorPayloadTypeRef);

            // Assert
            assertThat(result).isPresent();
            // Jackson по-умолчанию поставит 0 для int, если поля нет
            assertThat(result.get().schemaVersion()).isEqualTo(0);
            assertThat(result.get().payload().lastCursor()).isEqualTo("abc");
            verify(mockMetrics).incrementCounter(Metric.JOB_STATE_MIGRATION_SUCCESS, Tags.of("job_name", JOB_NAME));
        }
    }

    @Nested
    @DisplayName("Метод saveState()")
    class SaveStateTests {

        @Test
        @DisplayName("TC R-01, R-03, R-04: Успешное сохранение")
        void saveState_whenCalled_shouldSerializeAndPutToHash() throws JsonProcessingException {
            // Arrange
            JobExecutionState<CursorPayload> stateToSave = JobExecutionState.published();
            String expectedJson = objectMapper.writeValueAsString(stateToSave);

            // Act
            repository.saveState(JOB_NAME, TEST_DATE, stateToSave);

            // Assert
            verify(mockHashOperations).put(HASH_KEY, HASH_FIELD, expectedJson);
            verifyNoInteractions(mockMetrics);
        }

        @Test
        @DisplayName("TC R-06: Обработка ошибки сериализации")
        void saveState_whenSerializationFails_shouldThrowIllegalStateAndIncrementMetric() throws JsonProcessingException {
            // Arrange
            JobExecutionState<CursorPayload> stateToSave = JobExecutionState.failed("error");
            doThrow(new JsonProcessingException("Test error") {}).when(objectMapper).writeValueAsString(stateToSave);

            // Act & Assert
            assertThatExceptionOfType(IllegalStateException.class)
                    .isThrownBy(() -> repository.saveState(JOB_NAME, TEST_DATE, stateToSave))
                    .withMessage("Failed to serialize job state for job: " + JOB_NAME);

            verify(mockMetrics).incrementCounter(Metric.JOB_STATE_SERIALIZATION_ERROR, Tags.of("job_name", JOB_NAME));
            verify(mockHashOperations, never()).put(any(), any(), any());
        }

        @Test
        @DisplayName("TC R-07: Обработка ошибки подключения к Redis")
        void saveState_whenRedisIsUnavailable_shouldThrowRuntimeException() {
            // Arrange
            JobExecutionState<CursorPayload> stateToSave = JobExecutionState.inProgress(null);
            doThrow(new RedisConnectionFailureException("Connection lost")).when(mockHashOperations).put(anyString(), anyString(), anyString());

            // Act & Assert
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> repository.saveState(JOB_NAME, TEST_DATE, stateToSave))
                    .withMessage("Could not save job state to Redis");
            verifyNoInteractions(mockMetrics);
        }
    }
}