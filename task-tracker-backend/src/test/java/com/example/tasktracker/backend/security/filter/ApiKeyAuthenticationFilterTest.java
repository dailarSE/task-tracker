package com.example.tasktracker.backend.security.filter;

import com.example.tasktracker.backend.common.MdcKeys;
import com.example.tasktracker.backend.security.apikey.ApiKeyAuthentication;
import com.example.tasktracker.backend.security.apikey.ApiKeyValidator;
import com.example.tasktracker.backend.security.apikey.InvalidApiKeyException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock private ApiKeyValidator mockApiKeyValidator;
    @Mock private HttpServletRequest mockRequest;
    @Mock private HttpServletResponse mockResponse;
    @Mock private FilterChain mockFilterChain;

    @InjectMocks
    private ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @BeforeEach
    @AfterEach
    void clearSecurityContextAndMdc() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    @DisplayName("doFilterInternal: валидный API ключ и ID экземпляра -> должен установить Authentication и MDC")
    void doFilterInternal_whenValidKeyAndInstanceHeader_shouldSetAuthAndMdc() throws ServletException, IOException {
        // Arrange
        String validKey = "valid-key";
        String serviceId = "scheduler-service";
        String instanceId = "scheduler-pod-123";
        when(mockRequest.getHeader("X-API-Key")).thenReturn(validKey);
        when(mockRequest.getHeader("X-Service-Instance-Id")).thenReturn(instanceId);
        when(mockApiKeyValidator.getServiceIdIfValid(validKey)).thenReturn(Optional.of(serviceId));

        doAnswer(invocation -> {
            assertThat(MDC.get(MdcKeys.SERVICE_ID)).isEqualTo(serviceId);
            assertThat(MDC.get(MdcKeys.SERVICE_INSTANCE_ID)).isEqualTo(instanceId);
            return null;
        }).when(mockFilterChain).doFilter(mockRequest, mockResponse);

        // Act
        apiKeyAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(ApiKeyAuthentication.class);
        ApiKeyAuthentication apiKeyAuth = (ApiKeyAuthentication) auth;
        assertThat(apiKeyAuth.getServiceId()).isEqualTo(serviceId);
        assertThat(apiKeyAuth.getInstanceId()).isEqualTo(instanceId);

        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
        assertThat(MDC.get(MdcKeys.SERVICE_ID)).isNull();
        assertThat(MDC.get(MdcKeys.SERVICE_INSTANCE_ID)).isNull();
    }

    @Test
    @DisplayName("doFilterInternal: валидный API ключ, но отсутствует ID экземпляра -> должен использовать placeholder")
    void doFilterInternal_whenValidKeyButMissingInstanceHeader_shouldUsePlaceholder() throws ServletException, IOException {
        // Arrange
        String validKey = "valid-key";
        String serviceId = "scheduler-service";
        when(mockRequest.getHeader("X-API-Key")).thenReturn(validKey);
        when(mockRequest.getHeader("X-Service-Instance-Id")).thenReturn(null);
        when(mockApiKeyValidator.getServiceIdIfValid(validKey)).thenReturn(Optional.of(serviceId));

        doAnswer(invocation -> {
            assertThat(MDC.get(MdcKeys.SERVICE_INSTANCE_ID)).isEqualTo(ApiKeyAuthenticationFilter.UNKNOWN_INSTANCE_ID);
            return null;
        }).when(mockFilterChain).doFilter(mockRequest, mockResponse);

        // Act
        apiKeyAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Assert
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(ApiKeyAuthentication.class);
        ApiKeyAuthentication apiKeyAuth = (ApiKeyAuthentication) auth;
        assertThat(apiKeyAuth.getInstanceId()).isEqualTo(ApiKeyAuthenticationFilter.UNKNOWN_INSTANCE_ID);
    }

    @Test
    @DisplayName("doFilterInternal: Невалидный API ключ -> должен выбросить InvalidApiKeyException")
    void doFilterInternal_whenInvalidApiKey_shouldThrowException() {
        // Arrange
        String invalidKey = "invalid-key";
        when(mockRequest.getHeader("X-API-Key")).thenReturn(invalidKey);
        when(mockApiKeyValidator.getServiceIdIfValid(invalidKey)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatExceptionOfType(InvalidApiKeyException.class)
                .isThrownBy(() -> apiKeyAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain))
                .withMessage("Invalid API Key provided.");

        verifyNoInteractions(mockFilterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @DisplayName("doFilterInternal: Отсутствует или пуст заголовок с API ключом -> должен выбросить InvalidApiKeyException")
    @ParameterizedTest(name = "Для заголовка X-API-Key: \"{0}\"")
    @ValueSource(strings = {"", " ", "   "})
    void doFilterInternal_whenApiKeyHeaderIsMissingOrBlank_shouldThrowException(String blankKey) {
        // Arrange
        when(mockRequest.getHeader("X-API-Key")).thenReturn(blankKey);

        // Act & Assert
        assertThatExceptionOfType(InvalidApiKeyException.class)
                .isThrownBy(() -> apiKeyAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain))
                .withMessage("API Key is missing.");

        verifyNoInteractions(mockApiKeyValidator, mockFilterChain);
    }

    @Test
    void doFilterInternal_whenApiKeyHeaderIsNull_shouldThrowException() {
        // Arrange
        when(mockRequest.getHeader("X-API-Key")).thenReturn(null);

        // Act & Assert
        assertThatExceptionOfType(InvalidApiKeyException.class)
                .isThrownBy(() -> apiKeyAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain))
                .withMessage("API Key is missing.");

        verifyNoInteractions(mockApiKeyValidator, mockFilterChain);
    }

    @Test
    @DisplayName("doFilterInternal: Непредвиденный RuntimeException из валидатора -> должен быть проброшен дальше")
    void doFilterInternal_whenValidatorThrowsRuntimeException_shouldPropagateException() {
        // Arrange
        String key = "some-key";
        RuntimeException unexpectedException = new IllegalStateException("Database is down!");
        when(mockRequest.getHeader("X-API-Key")).thenReturn(key);
        when(mockApiKeyValidator.getServiceIdIfValid(key)).thenThrow(unexpectedException);

        // Act & Assert
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> apiKeyAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain))
                .isSameAs(unexpectedException);

        verifyNoInteractions(mockFilterChain);
    }
}