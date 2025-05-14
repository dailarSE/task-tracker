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
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для {@link BearerTokenProblemDetailsAuthenticationEntryPoint}.
 */
@ExtendWith(MockitoExtension.class)
class BearerTokenProblemDetailsAuthenticationEntryPointTest {

    @Mock
    private HandlerExceptionResolver mockHandlerExceptionResolver;

    @Mock
    private HttpServletRequest mockRequest;

    @Mock
    private HttpServletResponse mockResponse;

    @InjectMocks
    private BearerTokenProblemDetailsAuthenticationEntryPoint entryPoint;

    @Test
    @DisplayName("Конструктор: HandlerExceptionResolver null -> должен выбросить NullPointerException (через Lombok @RequiredArgsConstructor)")
    void constructor_whenHandlerExceptionResolverIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> new BearerTokenProblemDetailsAuthenticationEntryPoint(null))
                .withMessageContaining("handlerExceptionResolver");
    }


    @Test
    @DisplayName("commence: должен установить WWW-Authenticate заголовок и делегировать в HandlerExceptionResolver")
    void commence_shouldSetWwwAuthenticateHeaderAndDelegateToResolver() throws IOException, ServletException {
        // Arrange
        AuthenticationException mockAuthException = new BadCredentialsException("Test auth error");

        // Act
        entryPoint.commence(mockRequest, mockResponse, mockAuthException);

        // Assert
        verify(mockResponse).setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"task-tracker\"");
        verify(mockHandlerExceptionResolver).resolveException(
                eq(mockRequest),
                eq(mockResponse),
                isNull(), // handler object (третий параметр) обычно null для фильтров
                eq(mockAuthException)
        );
        verify(mockResponse, never()).sendError(anyInt(), anyString()); // Убеждаемся, что sendError не вызывался здесь
        verify(mockResponse, never()).setStatus(anyInt()); // И статус напрямую не менялся здесь (кроме как через resolver)
    }
}