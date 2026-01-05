package com.example.tasktracker.scheduler.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Юнит-тесты для {@link DefaultJobStateKeyProvider}.
 */
@DisplayName("Unit-тесты для DefaultJobStateKeyProvider")
class DefaultJobStateKeyProviderTest {

    private DefaultJobStateKeyProvider keyProvider;

    @BeforeEach
    void setUp() {
        keyProvider = new DefaultJobStateKeyProvider();
    }

    @Test
    @DisplayName("getHashKey(): должен возвращать ключ HASH'а, состоящий из префикса и имени джобы")
    void getHashKey_shouldReturnCorrectlyFormattedKey() {
        // Arrange
        String jobName = "my-awesome-job";

        // Act
        String hashKey = keyProvider.getHashKey(jobName);

        // Assert
        assertThat(hashKey).isEqualTo(DefaultJobStateKeyProvider.KEY_PREFIX + jobName);
    }

    @Test
    @DisplayName("getHashKey(): jobName=null -> должен выбросить NullPointerException")
    void getHashKey_whenJobNameIsNull_shouldThrowNpe() {
        // Act & Assert
        assertThatNullPointerException()
                .isThrownBy(() -> keyProvider.getHashKey(null))
                .withMessageContaining("jobName");
    }

    @Test
    @DisplayName("getHashField(): должен возвращать дату в формате ISO_LOCAL_DATE (YYYY-MM-DD)")
    void getHashField_shouldReturnDateInIsoFormat() {
        // Arrange
        LocalDate date = LocalDate.of(2025, 7, 15);

        // Act
        String hashField = keyProvider.getHashField(date);

        // Assert (Контракт формата даты)
        assertThat(hashField).isEqualTo("2025-07-15");
    }

    @Test
    @DisplayName("getHashField(): date=null -> должен выбросить NullPointerException")
    void getHashField_whenDateIsNull_shouldThrowNpe() {
        // Act & Assert
        assertThatNullPointerException()
                .isThrownBy(() -> keyProvider.getHashField(null))
                .withMessageContaining("date");
    }
}