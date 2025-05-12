package com.example.tasktracker.backend.security.details;

import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для {@link UserLoadingService}.
 */
@ExtendWith(MockitoExtension.class)
class UserLoadingServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserLoadingService userLoadingService;

    private static final String TEST_EMAIL = "test@example.com";

    @Test
    @DisplayName("loadUserByUsername должен выбросить UsernameNotFoundException, если пользователь не найден")
    void loadUserByUsername_whenUserNotFound_shouldThrowUsernameNotFoundException() {
        // Arrange
        // Настраиваем мок репозитория: вернуть пустой Optional при поиске по любому email
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatExceptionOfType(UsernameNotFoundException.class)
                .isThrownBy(() -> userLoadingService.loadUserByUsername(TEST_EMAIL))
                .withMessageContaining(TEST_EMAIL);

        verify(userRepository, times(1)).findByEmail(TEST_EMAIL);
    }

    @Test
    @DisplayName("loadUserByUsername должен вернуть AppUserDetails, если пользователь найден")
    void loadUserByUsername_whenUserFound_shouldReturnAppUserDetails() {
        User foundUser = new User();
        foundUser.setId(1L);
        foundUser.setEmail(TEST_EMAIL);
        foundUser.setPassword("hashedPasswordFromDB");

        when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(foundUser));

        // Act
        UserDetails userDetails = userLoadingService.loadUserByUsername(TEST_EMAIL);

        // Assert
        assertThat(userDetails).isNotNull().isInstanceOf(AppUserDetails.class);

        AppUserDetails appUserDetails = (AppUserDetails) userDetails;
        assertThat(appUserDetails.getId()).isEqualTo(foundUser.getId());
        assertThat(appUserDetails.getUsername()).isEqualTo(foundUser.getEmail()); // Email -> Username
        assertThat(appUserDetails.getPassword()).isEqualTo(foundUser.getPassword());
        assertThat(appUserDetails.getAuthorities()).isEmpty();

        verify(userRepository, times(1)).findByEmail(TEST_EMAIL);
    }
}