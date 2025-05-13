package com.example.tasktracker.backend.security.details;

import com.example.tasktracker.backend.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты для {@link AppUserDetails}.
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("ci")
class AppUserDetailsTest {

    @Mock
    private User mockUser;

    private static final Long USER_ID = 1L;
    private static final String USER_EMAIL = "test@example.com";
    private static final String USER_PASSWORD_HASH = "hashedPassword";

    @BeforeEach
    void setUp() {
        when(mockUser.getId()).thenReturn(USER_ID);
        when(mockUser.getEmail()).thenReturn(USER_EMAIL);
        when(mockUser.getPassword()).thenReturn(USER_PASSWORD_HASH);
    }

    @Test
    @DisplayName("Конструктор должен выбросить NullPointerException при null User")
    @MockitoSettings(strictness = Strictness.LENIENT)
    void constructor_whenUserIsNull_shouldThrowNullPointerException() {
        // Act & Assert
        assertThatNullPointerException()
                .isThrownBy(() -> new AppUserDetails(null));
    }

    @Test
    @DisplayName("Конструктор должен корректно создать объект с валидным User")
    void constructor_whenUserIsValid_shouldCreateObjectCorrectly() {
        // Act
        AppUserDetails userDetails = new AppUserDetails(mockUser);

        // Assert
        assertThat(userDetails.getId()).isEqualTo(USER_ID);
        assertThat(userDetails.getUsername()).isEqualTo(USER_EMAIL); // getUsername() возвращает email
        assertThat(userDetails.getPassword()).isEqualTo(USER_PASSWORD_HASH);
        assertThat(userDetails.getAuthorities()).isNotNull();
    }

    @Test
    @DisplayName("Методы статуса аккаунта должны возвращать true по умолчанию")
    void accountStatusMethods_shouldReturnTrueByDefault() {
        // Arrange
        AppUserDetails userDetails = new AppUserDetails(mockUser);

        // Act & Assert
        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isCredentialsNonExpired()).isTrue();
        assertThat(userDetails.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("getAuthorities должен возвращать пустую коллекцию")
    void getAuthorities_shouldReturnEmptyCollection() {
        // Arrange
        AppUserDetails userDetails = new AppUserDetails(mockUser);

        // Act
        Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();

        // Assert
        assertThat(authorities).isNotNull().isEmpty();
    }
}