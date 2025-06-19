package com.example.tasktracker.backend.security.filter;

import com.example.tasktracker.backend.security.apikey.ApiKeyAuthentication;
import com.example.tasktracker.backend.security.apikey.ApiKeyValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private ApiKeyValidator mockApiKeyValidator;
    @Mock
    private AuthenticationEntryPoint mockAuthenticationEntryPoint;
    @Mock
    private HttpServletRequest mockRequest;
    @Mock
    private HttpServletResponse mockResponse;
    @Mock
    private FilterChain mockFilterChain;

    @InjectMocks
    private ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @BeforeEach
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("doFilterInternal: Валидный API ключ -> должен установить Authentication и продолжить цепочку")
    void doFilterInternal_whenValidApiKey_shouldSetAuthenticationAndProceedChain() throws ServletException, IOException {
        // Arrange
        String validKey = "valid-key-123";
        when(mockRequest.getHeader("X-API-Key")).thenReturn(validKey);
        when(mockApiKeyValidator.isValid(validKey)).thenReturn(true);

        // Act
        apiKeyAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Assert
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull().isInstanceOf(ApiKeyAuthentication.class);
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo("internal-service");

        verify(mockFilterChain).doFilter(mockRequest, mockResponse);
        verifyNoInteractions(mockAuthenticationEntryPoint);
    }

    @Test
    @DisplayName("doFilterInternal: Невалидный API ключ -> должен вызвать AuthenticationEntryPoint и прервать цепочку")
    void doFilterInternal_whenInvalidApiKey_shouldCallEntryPointAndStopChain() throws ServletException, IOException {
        // Arrange
        String invalidKey = "invalid-key-456";
        when(mockRequest.getHeader("X-API-Key")).thenReturn(invalidKey);
        when(mockApiKeyValidator.isValid(invalidKey)).thenReturn(false);

        // Act
        apiKeyAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(mockAuthenticationEntryPoint).commence(eq(mockRequest), eq(mockResponse), any());
        verify(mockFilterChain, never()).doFilter(mockRequest, mockResponse);
    }

    @Test
    @DisplayName("doFilterInternal: Отсутствует заголовок с API ключом -> должен вызвать AuthenticationEntryPoint и прервать цепочку")
    void doFilterInternal_whenApiKeyHeaderIsMissing_shouldCallEntryPointAndStopChain() throws ServletException, IOException {
        // Arrange
        when(mockRequest.getHeader("X-API-Key")).thenReturn(null);

        // Act
        apiKeyAuthenticationFilter.doFilterInternal(mockRequest, mockResponse, mockFilterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(mockAuthenticationEntryPoint).commence(eq(mockRequest), eq(mockResponse), any());
        verify(mockFilterChain, never()).doFilter(mockRequest, mockResponse);
        verifyNoInteractions(mockApiKeyValidator); // Валидатор не должен вызываться, если ключа нет
    }
}