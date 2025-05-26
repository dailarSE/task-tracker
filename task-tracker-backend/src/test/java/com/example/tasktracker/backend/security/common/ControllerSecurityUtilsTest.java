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
        User user = new User(null, email, USER_PASSWORD_HASH, Instant.now(), Instant.now());
        // В AppUserDetails конструкторе Objects.requireNonNull для id.
        // Поэтому для этого теста нам нужно мокировать AppUserDetails или
        // создать User, который пройдет проверку AppUserDetails.
        // Проще мокнуть AppUserDetails, чтобы он возвращал null для getId().
        AppUserDetails mockUserDetails = Mockito.mock(AppUserDetails.class);
        when(mockUserDetails.getId()).thenReturn(null);
        when(mockUserDetails.getUsername()).thenReturn(email);
        when(mockUserDetails.getAuthorities()).thenReturn(Collections.emptyList());
        when(mockUserDetails.getPassword()).thenReturn(USER_PASSWORD_HASH);
        return mockUserDetails;
    }

    private Authentication createAuthentication(AppUserDetails userDetails, boolean authenticated) {
        TestingAuthenticationToken authToken = new TestingAuthenticationToken(
                userDetails,
                null, // credentials
                userDetails.getAuthorities()
        );
        authToken.setAuthenticated(authenticated);
        return authToken;
    }

    private Authentication createAuthenticationWithGenericUserDetails(UserDetails userDetails, boolean authenticated) {
        TestingAuthenticationToken authToken = new TestingAuthenticationToken(
                userDetails,
                null, // credentials
                userDetails.getAuthorities() // getAuthorities() у UserDetails
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
    @DisplayName("getAuthenticatedUserDetails: principal null, SecurityContext пуст -> должен выбросить IllegalStateException")
    void getAuthenticatedUserDetails_whenPrincipalIsNullAndNoAuthInContext_shouldThrowIllegalStateException() {
        // Arrange
        SecurityContextHolder.clearContext(); // Убедимся, что контекст пуст

        // Act & Assert
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ControllerSecurityUtils.getAuthenticatedUserDetails(null))
                .withMessage("Authenticated principal (AppUserDetails) is not available.");
    }

    @Test
    @DisplayName("getAuthenticatedUserDetails: principal не AppUserDetails, но AppUserDetails есть в SecurityContext -> должен вернуть AppUserDetails из контекста")
    void getAuthenticatedUserDetails_whenPrincipalIsNotAppUserDetailsButContextHasIt_shouldReturnUserDetailsFromContext() {
        // Arrange
        AppUserDetails userDetailsInContext = createAppUserDetails(USER_ID, USER_EMAIL);
        Authentication authInContext = createAuthentication(userDetailsInContext, true);
        setupSecurityContext(authInContext);

        Object nonAppUserDetailsPrincipal = "someOtherPrincipalObject"; // Principal другого типа

        // Act
        AppUserDetails result = ControllerSecurityUtils.getAuthenticatedUserDetails(nonAppUserDetailsPrincipal);

        // Assert
        assertThat(result).isSameAs(userDetailsInContext); // Должен вернуться userDetails из контекста
    }

    @Test
    @DisplayName("getAuthenticatedUserDetails: principal null, AppUserDetails есть в SecurityContext -> должен вернуть AppUserDetails из контекста")
    void getAuthenticatedUserDetails_whenPrincipalIsNullButContextHasIt_shouldReturnUserDetailsFromContext() {
        // Arrange
        AppUserDetails userDetailsInContext = createAppUserDetails(USER_ID, USER_EMAIL);
        Authentication authInContext = createAuthentication(userDetailsInContext, true);
        setupSecurityContext(authInContext);

        // Act
        AppUserDetails result = ControllerSecurityUtils.getAuthenticatedUserDetails(null);

        // Assert
        assertThat(result).isSameAs(userDetailsInContext);
    }


    @Test
    @DisplayName("getAuthenticatedUserDetails: principal не AppUserDetails, SecurityContext имеет неаутентифицированный Authentication -> должен выбросить IllegalStateException")
    void getAuthenticatedUserDetails_whenContextNotAuthenticated_shouldThrowIllegalStateException() {
        // Arrange
        // Authentication не AppUserDetails, и не аутентифицирован
        Authentication nonAuthenticatedAuth = new TestingAuthenticationToken("genericUser", "pass");
        nonAuthenticatedAuth.setAuthenticated(false);
        setupSecurityContext(nonAuthenticatedAuth);

        Object nonAppUserDetailsPrincipal = "someOtherPrincipalObject";

        // Act & Assert
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ControllerSecurityUtils.getAuthenticatedUserDetails(nonAppUserDetailsPrincipal))
                .withMessage("Authenticated principal (AppUserDetails) is not available.");
    }

    @Test
    @DisplayName("getAuthenticatedUserDetails: principal не AppUserDetails, SecurityContext имеет Authentication с principal другого типа -> должен выбросить IllegalStateException")
    void getAuthenticatedUserDetails_whenContextPrincipalIsNotAppUserDetails_shouldThrowIllegalStateException() {
        // Arrange
        UserDetails genericUserDetails = mock(UserDetails.class);
        when(genericUserDetails.getUsername()).thenReturn("generic");
        Authentication authWithGenericPrincipal = createAuthenticationWithGenericUserDetails(genericUserDetails, true);
        setupSecurityContext(authWithGenericPrincipal);

        Object nonAppUserDetailsPrincipal = "someOtherPrincipalObject";

        // Act & Assert
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ControllerSecurityUtils.getAuthenticatedUserDetails(nonAppUserDetailsPrincipal))
                .withMessage("Authenticated principal (AppUserDetails) is not available.");
    }

    @Test
    @DisplayName("getAuthenticatedUserDetails: principal является AppUserDetails, но его ID null -> должен выбросить IllegalStateException")
    void getAuthenticatedUserDetails_whenAppUserDetailsIdIsNull_shouldThrowIllegalStateException() {
        // Arrange
        AppUserDetails userDetailsWithNullId = createAppUserDetailsWithNullId(USER_EMAIL);

        // Act & Assert
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ControllerSecurityUtils.getAuthenticatedUserDetails(userDetailsWithNullId))
                .withMessage("Authenticated principal (AppUserDetails) has a null ID.");
    }

    // --- Тесты для getCurrentUserId(Object principal) ---

    @Test
    @DisplayName("getCurrentUserId: principal является AppUserDetails -> должен вернуть ID пользователя")
    void getCurrentUserId_whenPrincipalIsAppUserDetails_shouldReturnUserId() {
        // Arrange
        AppUserDetails userDetails = createAppUserDetails(USER_ID, USER_EMAIL);

        // Act
        Long result = ControllerSecurityUtils.getCurrentUserId(userDetails);

        // Assert
        assertThat(result).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("getCurrentUserId: principal null, SecurityContext пуст -> должен выбросить IllegalStateException")
    void getCurrentUserId_whenPrincipalIsNullAndNoAuthInContext_shouldThrowIllegalStateException() {
        // Arrange
        SecurityContextHolder.clearContext();

        // Act & Assert
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ControllerSecurityUtils.getCurrentUserId(null))
                .withMessage("Authenticated principal (AppUserDetails) is not available.");
    }

    @Test
    @DisplayName("getCurrentUserId: principal является AppUserDetails, но его ID null -> должен выбросить IllegalStateException")
    void getCurrentUserId_whenAppUserDetailsIdIsNull_shouldThrowIllegalStateExceptionForId() {
        // Arrange
        AppUserDetails userDetailsWithNullId = createAppUserDetailsWithNullId(USER_EMAIL);

        // Act & Assert
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> ControllerSecurityUtils.getCurrentUserId(userDetailsWithNullId))
                .withMessage("Authenticated principal (AppUserDetails) has a null ID.");
    }
}