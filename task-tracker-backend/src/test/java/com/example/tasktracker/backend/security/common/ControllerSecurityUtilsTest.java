package com.example.tasktracker.backend.security.common;

import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты для {@link ControllerSecurityUtils}.
 * Проверяют различные сценарии извлечения и валидации principal.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ControllerSecurityUtilsTest {

    private static final Long USER_ID = 123L;
    private static final String USER_EMAIL = "test@example.com";
    private static final String USER_PASSWORD_HASH = "hashedPassword";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    // --- Вспомогательные методы ---
    private AppUserDetails createAppUserDetails(Long id, String email) {
        User user = new User(id, email, USER_PASSWORD_HASH, Instant.now(), Instant.now());
        return new AppUserDetails(user);
    }

    private AppUserDetails createAppUserDetailsWithNullId(String email) {
        AppUserDetails mockUserDetails = Mockito.mock(AppUserDetails.class);
        when(mockUserDetails.getId()).thenReturn(null);
        when(mockUserDetails.getUsername()).thenReturn(email);
        when(mockUserDetails.getAuthorities()).thenReturn(Collections.emptyList());
        when(mockUserDetails.getAuthorities()).thenReturn(Collections.emptyList());
        when(mockUserDetails.getPassword()).thenReturn(USER_PASSWORD_HASH);
        return mockUserDetails;
    }

    private Authentication createAuthentication(UserDetails userDetails, boolean authenticated) {
        TestingAuthenticationToken authToken = new TestingAuthenticationToken(
                userDetails,
                null, // credentials
                userDetails.getAuthorities()
        );
        authToken.setAuthenticated(authenticated);
        return authToken;
    }

    private void setupSecurityContext(Authentication authentication) {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    // --- Тесты для getAuthenticatedUserDetails(Object principal) ---

    @Test
    @DisplayName("getAuthenticatedUserDetails: principal является AppUserDetails -> должен вернуть AppUserDetails")
    void getAuthenticatedUserDetails_whenPrincipalIsAppUserDetails_shouldReturnUserDetails() {
        // Arrange
        AppUserDetails userDetails = createAppUserDetails(USER_ID, USER_EMAIL);

        // Act
        AppUserDetails result = ControllerSecurityUtils.getAuthenticatedUserDetails(userDetails);

        // Assert
        assertThat(result).isSameAs(userDetails);
    }

    @Test
    @DisplayName("getAuthenticatedUserDetails: principal null -> должен выбросить IllegalStateException")
    void getAuthenticatedUserDetails_whenPrincipalIsNull_shouldThrowIllegalStateException() {
        // Act & Assert
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ControllerSecurityUtils.getAuthenticatedUserDetails(null))
                .withMessage("Authenticated principal (AppUserDetails) is not available.");
    }

    // ТЕСТЫ, КОТОРЫЕ НУЖНО ПЕРЕПИСАТЬ/ИЗМЕНИТЬ:
    // Оригинальные названия:
    // getAuthenticatedUserDetails_whenPrincipalIsNotAppUserDetailsButContextHasIt_shouldReturnUserDetailsFromContext
    // getAuthenticatedUserDetails_whenPrincipalIsNullButContextHasIt_shouldReturnUserDetailsFromContext

    @Test
    @DisplayName("getAuthenticatedUserDetails: principal не AppUserDetails, AppUserDetails есть в SecurityContext -> должен выбросить IllegalStateException")
    void getAuthenticatedUserDetails_whenPrincipalIsNotAppUserDetails_shouldThrowIllegalStateException() {
        // Arrange
        AppUserDetails userDetailsInContext = createAppUserDetails(USER_ID, USER_EMAIL);
        Authentication authInContext = createAuthentication(userDetailsInContext, true);
        setupSecurityContext(authInContext); // Контекст ЗАПОЛНЕН корректно

        Object nonAppUserDetailsPrincipal = "someOtherPrincipalObject"; // Principal другого типа передан в аргумент

        // Act & Assert
        // Теперь, если principal в аргументе не AppUserDetails, метод не будет смотреть в контекст
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ControllerSecurityUtils.getAuthenticatedUserDetails(nonAppUserDetailsPrincipal))
                .withMessage("Authenticated principal (AppUserDetails) is not available.");
    }

    @Test
    @DisplayName("getAuthenticatedUserDetails: principal null, AppUserDetails есть в SecurityContext -> должен выбросить IllegalStateException")
    void getAuthenticatedUserDetails_whenPrincipalIsNull_andContextHasIt_shouldThrowIllegalStateException() {
        // Arrange
        AppUserDetails userDetailsInContext = createAppUserDetails(USER_ID, USER_EMAIL);
        Authentication authInContext = createAuthentication(userDetailsInContext, true);
        setupSecurityContext(authInContext); // Контекст ЗАПОЛНЕН корректно

        // Act & Assert
        // Теперь, если principal в аргументе null, метод не будет смотреть в контекст
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ControllerSecurityUtils.getAuthenticatedUserDetails(null))
                .withMessage("Authenticated principal (AppUserDetails) is not available.");
    }

    // Остальные тесты, которые проверяют сценарии, когда контекст НЕ СОДЕРЖИТ ожидаемого principal:
    // Они должны остаться без изменений, так как их логика (чтобы бросать IllegalStateException)
    // теперь совпадает с логикой getAppUserDetails, которая не пытается искать в контексте.

    @Test
    @DisplayName("getAuthenticatedUserDetails: principal не AppUserDetails, SecurityContext имеет неаутентифицированный Authentication -> должен выбросить IllegalStateException")
    void getAuthenticatedUserDetails_whenContextNotAuthenticated_shouldThrowIllegalStateException() {
        Authentication nonAuthenticatedAuth = createAuthentication(mock(UserDetails.class), false); // Неаутентифицированный
        setupSecurityContext(nonAuthenticatedAuth);

        Object nonAppUserDetailsPrincipal = "someOtherPrincipalObject";

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ControllerSecurityUtils.getAuthenticatedUserDetails(nonAppUserDetailsPrincipal))
                .withMessage("Authenticated principal (AppUserDetails) is not available.");
    }

    @Test
    @DisplayName("getAuthenticatedUserDetails: principal не AppUserDetails, SecurityContext имеет Authentication с principal другого типа -> должен выбросить IllegalStateException")
    void getAuthenticatedUserDetails_whenContextPrincipalIsNotAppUserDetails_shouldThrowIllegalStateException() {
        UserDetails genericUserDetails = mock(UserDetails.class);
        when(genericUserDetails.getUsername()).thenReturn("generic");
        Authentication authWithGenericPrincipal = createAuthentication(genericUserDetails, true);
        setupSecurityContext(authWithGenericPrincipal);

        Object nonAppUserDetailsPrincipal = "someOtherPrincipalObject";

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ControllerSecurityUtils.getAuthenticatedUserDetails(nonAppUserDetailsPrincipal))
                .withMessage("Authenticated principal (AppUserDetails) is not available.");
    }

    @Test
    @DisplayName("getAuthenticatedUserDetails: principal является AppUserDetails, но его ID null -> должен выбросить IllegalStateException")
    void getAuthenticatedUserDetails_whenAppUserDetailsIdIsNull_shouldThrowIllegalStateException() {
        AppUserDetails userDetailsWithNullId = createAppUserDetailsWithNullId(USER_EMAIL);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ControllerSecurityUtils.getAuthenticatedUserDetails(userDetailsWithNullId))
                .withMessage("Authenticated principal (AppUserDetails) has a null ID.");
    }

    // --- Тесты для getCurrentUserId(Object principal) ---

    @Test
    @DisplayName("getCurrentUserId: principal является AppUserDetails -> должен вернуть ID пользователя")
    void getCurrentUserId_whenPrincipalIsAppUserDetails_shouldReturnUserId() {
        AppUserDetails userDetails = createAppUserDetails(USER_ID, USER_EMAIL);
        Long result = ControllerSecurityUtils.getCurrentUserId(userDetails);
        assertThat(result).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("getCurrentUserId: principal null -> должен выбросить IllegalStateException")
    void getCurrentUserId_whenPrincipalIsNull_shouldThrowIllegalStateExceptionForId() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ControllerSecurityUtils.getCurrentUserId(null))
                .withMessage("Authenticated principal (AppUserDetails) is not available.");
    }

    @Test
    @DisplayName("getCurrentUserId: principal является AppUserDetails, но его ID null -> должен выбросить IllegalStateException")
    void getCurrentUserId_whenAppUserDetailsIdIsNull_shouldThrowIllegalStateExceptionForId() {
        AppUserDetails userDetailsWithNullId = createAppUserDetailsWithNullId(USER_EMAIL);
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ControllerSecurityUtils.getCurrentUserId(userDetailsWithNullId))
                .withMessage("Authenticated principal (AppUserDetails) has a null ID.");
    }
}