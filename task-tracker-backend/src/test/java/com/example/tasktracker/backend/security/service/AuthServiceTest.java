package com.example.tasktracker.backend.security.service;

import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.security.dto.AuthResponse;
import com.example.tasktracker.backend.security.dto.LoginRequest;
import com.example.tasktracker.backend.security.dto.RegisterRequest;
import com.example.tasktracker.backend.security.exception.PasswordMismatchException;
import com.example.tasktracker.backend.security.exception.UserAlreadyExistsException;
import com.example.tasktracker.backend.security.jwt.JwtIssuer;
import com.example.tasktracker.backend.security.jwt.JwtProperties;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для {@link AuthService}.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository mockUserRepository;
    @Mock
    private PasswordEncoder mockPasswordEncoder;
    @Mock
    private AuthenticationManager mockAuthenticationManager;
    @Mock
    private JwtIssuer mockJwtIssuer;
    @Mock
    private JwtProperties mockJwtProperties;

    @InjectMocks
    private AuthService authService;

    @Captor
    private ArgumentCaptor<User> userArgumentCaptor;
    @Captor
    private ArgumentCaptor<Authentication> authenticationArgumentCaptor;
    @Captor
    private ArgumentCaptor<UsernamePasswordAuthenticationToken> upatArgumentCaptor;


    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_HASHED_PASSWORD = "hashedPassword123";
    private static final String TEST_JWT_TOKEN = "test.jwt.token.string";
    private static final Long TEST_EXPIRATION_MS = 3600000L;
    private static final Long SAVED_USER_ID = 1L;


    // --- Тесты для метода register ---

    @Test
    @DisplayName("register: RegisterRequest null -> должен выбросить NullPointerException")
    void register_whenRequestIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> authService.register(null))
                .withMessageContaining("request is marked non-null but is null");
    }

    @Test
    @DisplayName("register: Пароли не совпадают -> должен выбросить PasswordMismatchException")
    void register_whenPasswordsDoNotMatch_shouldThrowPasswordMismatchException() {
        // Arrange
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, "differentPassword");

        // Act & Assert
        assertThatExceptionOfType(PasswordMismatchException.class)
                .isThrownBy(() -> authService.register(request))
                .withMessage("Passwords do not match.");

        verifyNoInteractions(mockUserRepository, mockPasswordEncoder, mockJwtIssuer);
    }

    @Test
    @DisplayName("register: Пользователь с таким email уже существует -> должен выбросить UserAlreadyExistsException")
    void register_whenUserEmailAlreadyExists_shouldThrowUserAlreadyExistsException() {
        // Arrange
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);
        when(mockUserRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

        // Act & Assert
        assertThatExceptionOfType(UserAlreadyExistsException.class)
                .isThrownBy(() -> authService.register(request))
                .withMessage("User with email " + TEST_EMAIL + " already exists.");

        verify(mockUserRepository).existsByEmail(TEST_EMAIL);
        verifyNoInteractions(mockPasswordEncoder); // Кодирование пароля не должно произойти
        verify(mockUserRepository, never()).save(any(User.class)); // Сохранение не должно произойти
        verifyNoInteractions(mockJwtIssuer);
    }

    @Test
    @DisplayName("register: Валидный запрос -> должен сохранить пользователя, сгенерировать токен и вернуть AuthResponse")
    void register_whenRequestIsValid_shouldSaveUserEncodePasswordAndReturnAuthResponseWithToken() {
        // Arrange
        when(mockJwtProperties.getExpirationMs()).thenReturn(TEST_EXPIRATION_MS);
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);
        when(mockUserRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(mockPasswordEncoder.encode(TEST_PASSWORD)).thenReturn(TEST_HASHED_PASSWORD);

        User savedUser = new User(); // Пользователь, который "вернет" мок save
        savedUser.setId(SAVED_USER_ID);
        savedUser.setEmail(TEST_EMAIL);
        savedUser.setPassword(TEST_HASHED_PASSWORD);
        // createdAt и updatedAt будут установлены JPA Auditing, здесь не мокаем

        when(mockUserRepository.save(any(User.class))).thenReturn(savedUser);
        when(mockJwtIssuer.generateToken(any(Authentication.class))).thenReturn(TEST_JWT_TOKEN);

        // Act
        AuthResponse authResponse = authService.register(request);

        // Assert
        verify(mockUserRepository).existsByEmail(TEST_EMAIL);
        verify(mockPasswordEncoder).encode(TEST_PASSWORD);
        verify(mockUserRepository).save(userArgumentCaptor.capture());
        User userToSave = userArgumentCaptor.getValue();
        assertThat(userToSave.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(userToSave.getPassword()).isEqualTo(TEST_HASHED_PASSWORD);

        verify(mockJwtIssuer).generateToken(authenticationArgumentCaptor.capture());
        Authentication generatedAuth = authenticationArgumentCaptor.getValue();
        assertThat(generatedAuth.getPrincipal()).isInstanceOf(AppUserDetails.class);
        AppUserDetails principalDetails = (AppUserDetails) generatedAuth.getPrincipal();
        assertThat(principalDetails.getId()).isEqualTo(SAVED_USER_ID); // Проверяем ID из savedUser
        assertThat(principalDetails.getUsername()).isEqualTo(TEST_EMAIL);

        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getAccessToken()).isEqualTo(TEST_JWT_TOKEN);
        assertThat(authResponse.getTokenType()).isEqualTo("Bearer");
        assertThat(authResponse.getExpiresIn()).isEqualTo(TEST_EXPIRATION_MS);
    }

    // --- Тесты для метода login ---

    @Test
    @DisplayName("login: LoginRequest null -> должен выбросить NullPointerException")
    void login_whenLoginRequestIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> authService.login(null))
                .withMessageContaining("request is marked non-null but is null");
    }

    @Test
    @DisplayName("login: Валидные креды -> должен аутентифицировать, сгенерировать токен и вернуть AuthResponse")
    void login_whenCredentialsAreValid_shouldAuthenticateAndReturnAuthResponseWithToken() {
        // Arrange
        when(mockJwtProperties.getExpirationMs()).thenReturn(TEST_EXPIRATION_MS);
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

        User userFromDb = new User(); // Пользователь, который "вернет" UserDetailsService
        userFromDb.setId(SAVED_USER_ID);
        userFromDb.setEmail(TEST_EMAIL);
        userFromDb.setPassword(TEST_HASHED_PASSWORD); // Хешированный пароль, который будет в UserDetails
        AppUserDetails authenticatedUserDetails = new AppUserDetails(userFromDb);
        Authentication successfulAuthentication = new TestingAuthenticationToken(
                authenticatedUserDetails, TEST_PASSWORD, authenticatedUserDetails.getAuthorities()
        );
        // Настраиваем AuthenticationManager
        when(mockAuthenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(successfulAuthentication);

        when(mockJwtIssuer.generateToken(successfulAuthentication)).thenReturn(TEST_JWT_TOKEN);

        // Act
        AuthResponse authResponse = authService.login(request);

        // Assert
        verify(mockAuthenticationManager).authenticate(upatArgumentCaptor.capture());
        UsernamePasswordAuthenticationToken upat = upatArgumentCaptor.getValue();
        assertThat(upat.getName()).isEqualTo(TEST_EMAIL);
        assertThat(upat.getCredentials()).isEqualTo(TEST_PASSWORD);

        verify(mockJwtIssuer).generateToken(successfulAuthentication);

        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getAccessToken()).isEqualTo(TEST_JWT_TOKEN);
        assertThat(authResponse.getTokenType()).isEqualTo("Bearer");
        assertThat(authResponse.getExpiresIn()).isEqualTo(TEST_EXPIRATION_MS);
    }

    @Test
    @DisplayName("login: Невалидные креды (AuthenticationManager выбрасывает исключение) -> должен пробросить исключение")
    void login_whenAuthenticationManagerThrowsAuthenticationException_shouldPropagateException() {
        // Arrange
        LoginRequest request = new LoginRequest(TEST_EMAIL, "wrongPassword");
        when(mockAuthenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials for test"));

        // Act & Assert
        assertThatExceptionOfType(BadCredentialsException.class)
                .isThrownBy(() -> authService.login(request))
                .withMessage("Bad credentials for test");

        verify(mockJwtIssuer, never()).generateToken(any(Authentication.class)); // Токен не должен генерироваться
    }
}