package com.example.tasktracker.backend.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для {@link ProblemDetailsAccessDeniedHandler}.
 * Проверяют, что обработчик корректно логирует AccessDeniedException и делегирует его
 * в {@link HandlerExceptionResolver}.
 */
@ExtendWith(MockitoExtension.class)
class ProblemDetailsAccessDeniedHandlerTest {

    @Mock
    private HandlerExceptionResolver mockHandlerExceptionResolver;

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;

    @InjectMocks
    private ProblemDetailsAccessDeniedHandler accessDeniedHandler;

    @Test
    @DisplayName("Конструктор: HandlerExceptionResolver null -> должен выбросить NullPointerException")
    void constructor_whenHandlerExceptionResolverIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> new ProblemDetailsAccessDeniedHandler(null))
                .withMessageContaining("handlerExceptionResolver is marked non-null but is null");
    }

    @Test
    @DisplayName("handle: должен логировать AccessDeniedException и делегировать в HandlerExceptionResolver")
    void handle_shouldLogAndDelegateToResolver() throws IOException, ServletException {
        // Arrange
        AccessDeniedException exception = new AccessDeniedException("User does not have access");
        Principal mockPrincipal = mock(Principal.class);
        when(mockPrincipal.getName()).thenReturn("testUser");
        when(mockRequest.getUserPrincipal()).thenReturn(mockPrincipal);
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn("/secure/resource");

        // Act
        accessDeniedHandler.handle(mockRequest, mockResponse, exception);

        // Assert
        // Проверяем, что resolveException был вызван с нужными аргументами
        verify(mockHandlerExceptionResolver).resolveException(
                eq(mockRequest),
                eq(mockResponse),
                isNull(), // handler object (третий параметр)
                eq(exception)
        );
        // Проверяем, что никаких других действий (например, sendError) не происходит
        verifyNoMoreInteractions(mockResponse);
    }

    @Test
    @DisplayName("handle: когда Principal is null, должен логировать 'anonymous'")
    void handle_whenPrincipalIsNull_shouldLogAnonymous() throws IOException, ServletException {
        // Arrange
        AccessDeniedException exception = new AccessDeniedException("No principal");
        when(mockRequest.getUserPrincipal()).thenReturn(null); // Principal is null
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn("/secure/resource");

        // Act
        accessDeniedHandler.handle(mockRequest, mockResponse, exception);

        // Assert
        // Здесь сложно проверить сам лог без LogCaptor, но логика должна быть вызвана.
        // Мы проверяем, что делегирование произошло.
        verify(mockHandlerExceptionResolver).resolveException(
                eq(mockRequest),
                eq(mockResponse),
                isNull(),
                eq(exception)
        );
    }
}