package com.example.tasktracker.backend.security.apikey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyValidatorTest {

    @Mock
    private ApiKeyProperties mockApiKeyProperties;

    @InjectMocks
    private ApiKeyValidator apiKeyValidator;

    private final String validKey1 = "key-for-scheduler";
    private final String serviceId1 = "scheduler-service";

    @BeforeEach
    void setUp() {
        when(mockApiKeyProperties.getKeysToServices())
                .thenReturn(Map.of(validKey1, serviceId1));
    }

    @Test
    @DisplayName("getServiceIdIfValid: валидный ключ -> должен вернуть Optional с ID сервиса")
    void getServiceIdIfValid_whenKeyIsValid_shouldReturnOptionalWithServiceId() {
        Optional<String> result = apiKeyValidator.getServiceIdIfValid(validKey1);
        assertThat(result).isPresent().contains(serviceId1);
    }

    @Test
    @DisplayName("getServiceIdIfValid: невалидный ключ -> должен вернуть пустой Optional")
    void getServiceIdIfValid_whenKeyIsInvalid_shouldReturnEmptyOptional() {
        Optional<String> result = apiKeyValidator.getServiceIdIfValid("invalid-key");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getServiceIdIfValid: карта ключей пуста -> должен вернуть пустой Optional")
    void getServiceIdIfValid_whenKeyMapIsEmpty_shouldReturnEmptyOptional() {
        when(mockApiKeyProperties.getKeysToServices()).thenReturn(Collections.emptyMap());
        Optional<String> result = apiKeyValidator.getServiceIdIfValid(validKey1);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getServiceIdIfValid: карта ключей null -> должен вернуть пустой Optional")
    void getServiceIdIfValid_whenKeyMapIsNull_shouldReturnEmptyOptional() {
        when(mockApiKeyProperties.getKeysToServices()).thenReturn(null);
        Optional<String> result = apiKeyValidator.getServiceIdIfValid(validKey1);
        assertThat(result).isEmpty();
    }
}