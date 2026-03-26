package com.example.tasktracker.emailsender.pipeline.ratelimit;

import com.example.tasktracker.emailsender.config.EmailSenderProperties;
import com.example.tasktracker.emailsender.exception.infrastructure.StateStoreInfrastructureException;
import io.github.bucket4j.BlockingBucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Bucket4jRpsLimiterTest {

    private static final int MAX_CHUNK = 20;
    private static final Duration TIMEOUT = Duration.ofMillis(100);

    private final EmailSenderProperties.RateLimitProperties props = new EmailSenderProperties.RateLimitProperties();
    private final BlockingBucket bucket = mock(BlockingBucket.class);

    private Bucket4jRpsLimiter limiter;

    @BeforeEach
    void setUp() {
        props.setTokenChunkSize(MAX_CHUNK);
        props.setAcquisitionTimeout(TIMEOUT);
        limiter = new Bucket4jRpsLimiter(bucket, props);
    }

    @ParameterizedTest(name = "Requested: {0} -> Expected Chunk: {1}")
    @CsvSource({
            "50, 20", // Больше лимита чанка -> берем лимит
            "10, 10", // Меньше лимита чанка -> берем сколько просили
            "20, 20"  // Ровно лимит
    })
    @DisplayName("Success: Should take correct amount of tokens based on chunk size")
    void shouldAcquireCorrectAmount(int requested, int expectedToTake) throws Exception {
        // Given: Базовый контракт Bucket4j: возвращает true, если токены выданы
        when(bucket.tryConsume(eq((long) expectedToTake), eq(TIMEOUT))).thenReturn(true);

        // When
        int actualTaken = limiter.acquire(requested);

        // Then
        assertEquals(expectedToTake, actualTaken, "Limiter must respect maxChunkSize policy");
    }

    @Test
    @DisplayName("Failure: Should throw RateLimitExceededException when bucket returns false (timeout)")
    void shouldThrowOnBucketTimeout() throws Exception {
        // Given: Токенов нет, Bucket4j подождал TIMEOUT и вернул false
        when(bucket.tryConsume(anyLong(), any())).thenReturn(false);

        // Act & Assert
        assertThrows(RateLimitExceededException.class, () -> limiter.acquire(10));
    }

    @Test
    @DisplayName("Failure: Should wrap Redis/Infrastructure errors into StateStoreInfrastructureException")
    void shouldHandleInfrastructureFailure() throws Exception {
        // Given: Redis недоступен или произошла ошибка в драйвере Lettuce
        when(bucket.tryConsume(anyLong(), any()))
                .thenThrow(new RuntimeException("Redis connection lost"));

        // Act & Assert
        var ex = assertThrows(StateStoreInfrastructureException.class, () -> limiter.acquire(5));
        assertTrue(ex.getMessage().contains("infrastructure failure"));
    }

    @Test
    @DisplayName("Interruption: Should propagate InterruptedException and preserve thread state")
    void shouldHandleInterruption() throws Exception {
        // Given: Поток прерван во время ожидания токенов
        when(bucket.tryConsume(anyLong(), any())).thenThrow(new InterruptedException("Stop!"));

        // Act & Assert
        assertThrows(InterruptedException.class, () -> limiter.acquire(5));
    }
}