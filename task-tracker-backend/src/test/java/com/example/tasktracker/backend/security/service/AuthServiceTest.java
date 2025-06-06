package com.example.tasktracker.backend.security.service;

import com.example.tasktracker.backend.kafka.service.EmailNotificationOrchestratorService;
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для {@link AuthService}.
 * Фокусируются на бизнес-логике регистрации и аутентификации,
 * а также на корректном взаимодействии с зависимыми сервисами.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository mockUserRepository;
    @Mock
    private EmailNotificationOrchestratorService mockNotificationService;
    @Mock
    private PasswordEncoder mockPasswordEncoder;
    @Mock
    private AuthenticationManager mockAuthenticationManager;
    @Mock
    private JwtIssuer mockJwtIssuer;
    @Mock
    private JwtProperties mockJwtProperties;
    @Mock
    private AuthService selfInjectedMock;

    @InjectMocks
    private AuthService authService;

    @Captor
    private ArgumentCaptor<User> userArgumentCaptor;
    @Captor
    private ArgumentCaptor<UsernamePasswordAuthenticationToken> upatArgumentCaptor;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_HASHED_PASSWORD = "hashedPassword123";
    private static final String TEST_JWT_TOKEN = "test.jwt.token.string";
    private static final long TEST_EXPIRATION_MS = 3600000L;
    private static final long TEST_EXPIRATION_SECONDS = TEST_EXPIRATION_MS / 1000;
    private static final Long SAVED_USER_ID = 1L;

    private User mockSavedUser;

    @BeforeEach
    void setUpForEachTestCase() {
        mockSavedUser = new User();
        mockSavedUser.setId(SAVED_USER_ID);
        mockSavedUser.setEmail(TEST_EMAIL);
        mockSavedUser.setPassword(TEST_HASHED_PASSWORD);

        // Общие настройки моков, используемые в нескольких тестах
        lenient().when(mockPasswordEncoder.encode(TEST_PASSWORD)).thenReturn(TEST_HASHED_PASSWORD);
        lenient().when(mockUserRepository.saveAndFlush(any(User.class))).thenReturn(mockSavedUser);
        lenient().when(mockJwtIssuer.generateToken(any(Authentication.class))).thenReturn(TEST_JWT_TOKEN);
        lenient().when(mockJwtProperties.getExpirationMs()).thenReturn(TEST_EXPIRATION_MS);
    }

    @AfterEach
    void tearDownForEachTestCase() {
        MDC.clear(); // Очищаем MDC после каждого теста
    }

    /**
     * Тесты для метода {@link AuthService#register(RegisterRequest)}.
     */
    @Nested
    @DisplayName("Метод register()")
    class RegisterTests {

        @Test
        @DisplayName("RegisterRequest null -> должен выбросить NullPointerException")
        void register_whenRequestIsNull_shouldThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> authService.register(null));
        }

        @Test
        @DisplayName("Пароли не совпадают -> должен выбросить PasswordMismatchException")
        void register_whenPasswordsDoNotMatch_shouldThrowPasswordMismatchException() {
            RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, "differentPassword");
            assertThatExceptionOfType(PasswordMismatchException.class)
                    .isThrownBy(() -> authService.register(request));
            verifyNoInteractions(mockUserRepository, mockPasswordEncoder, mockJwtIssuer, selfInjectedMock, mockNotificationService);
        }

        @Test
        @DisplayName("Email уже существует -> должен выбросить UserAlreadyExistsException")
        void register_whenEmailAlreadyExists_shouldThrowUserAlreadyExistsException() {
            RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);
            when(mockUserRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

            assertThatExceptionOfType(UserAlreadyExistsException.class)
                    .isThrownBy(() -> authService.register(request))
                    .satisfies(ex -> assertThat(ex.getEmail()).isEqualTo(TEST_EMAIL));

            verify(mockUserRepository).existsByEmail(TEST_EMAIL);
            verify(mockUserRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Состояние гонки (DataIntegrityViolationException) -> должен выбросить UserAlreadyExistsException")
        void register_whenRaceConditionOnSave_shouldThrowUserAlreadyExistsException() {
            RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);
            when(mockUserRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            when(mockUserRepository.saveAndFlush(any(User.class)))
                    .thenThrow(new DataIntegrityViolationException("Simulated unique constraint violation"));
            // Настраиваем self-инъекцию для проверки в новой транзакции
            when(selfInjectedMock.existsByEmailInNewTransaction(TEST_EMAIL)).thenReturn(true);

            assertThatExceptionOfType(UserAlreadyExistsException.class)
                    .isThrownBy(() -> authService.register(request));

            verify(selfInjectedMock).existsByEmailInNewTransaction(TEST_EMAIL);
        }

        @Test
        @DisplayName("Валидный запрос -> должен сохранить пользователя, инициировать уведомление и вернуть токен")
        void register_whenRequestIsValid_shouldSaveUserInitiateNotificationAndReturnToken() {
            RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);
            when(mockUserRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            lenient().when(selfInjectedMock.existsByEmailInNewTransaction(anyString())).thenReturn(false);

            Locale expectedLocale = Locale.GERMAN;
            try (MockedStatic<LocaleContextHolder> mockedLocaleContext = mockStatic(LocaleContextHolder.class)) {
                mockedLocaleContext.when(LocaleContextHolder::getLocale).thenReturn(expectedLocale);

                AuthResponse authResponse = authService.register(request);

                verify(mockUserRepository).saveAndFlush(userArgumentCaptor.capture());
                assertThat(userArgumentCaptor.getValue().getEmail()).isEqualTo(TEST_EMAIL);

                verify(mockNotificationService).scheduleInitialEmailNotification(eq(mockSavedUser), eq(expectedLocale.toLanguageTag()));

                verify(mockJwtIssuer).generateToken(any(Authentication.class));
                assertThat(authResponse).isNotNull();
                assertThat(authResponse.getAccessToken()).isEqualTo(TEST_JWT_TOKEN);
            }
        }

        @Test
        @DisplayName("Ошибка при инициации Kafka -> регистрация должна пройти успешно, ошибка логируется")
        void register_whenKafkaInitiationFails_shouldSucceedAndNotThrow() {
            RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);
            when(mockUserRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            when(selfInjectedMock.existsByEmailInNewTransaction(anyString())).thenReturn(false);

            doThrow(new RuntimeException("Simulated Kafka Initiation Error"))
                    .when(mockNotificationService).scheduleInitialEmailNotification(any(User.class), anyString());

            AuthResponse authResponse = assertDoesNotThrow(() -> authService.register(request),
                    "register() should not throw an exception when Kafka initiation fails.");

            verify(mockUserRepository, atLeastOnce()).saveAndFlush(any(User.class));
            verify(mockJwtIssuer, atLeastOnce()).generateToken(any(Authentication.class));
            assertThat(authResponse).isNotNull();
            assertThat(authResponse.getAccessToken()).isEqualTo(TEST_JWT_TOKEN);
            verify(mockNotificationService, atLeastOnce()).scheduleInitialEmailNotification(any(User.class), anyString());
        }
    }

    /**
     * Тесты для метода {@link AuthService#login(LoginRequest)}.
     */
    @Nested
    @DisplayName("Метод login()")
    class LoginTests {

        @Test
        @DisplayName("LoginRequest null -> должен выбросить NullPointerException")
        void login_whenRequestIsNull_shouldThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> authService.login(null));
        }

        @Test
        @DisplayName("Валидные креды -> должен аутентифицировать и вернуть AuthResponse")
        void login_whenCredentialsAreValid_shouldAuthenticateAndReturnAuthResponse() {
            LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);
            AppUserDetails authenticatedUserDetails = new AppUserDetails(mockSavedUser);
            Authentication successfulAuthentication = new TestingAuthenticationToken(
                    authenticatedUserDetails, TEST_PASSWORD, authenticatedUserDetails.getAuthorities()
            );
            when(mockAuthenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(successfulAuthentication);

            AuthResponse authResponse = authService.login(request);

            verify(mockAuthenticationManager).authenticate(upatArgumentCaptor.capture());
            UsernamePasswordAuthenticationToken upat = upatArgumentCaptor.getValue();
            assertThat(upat.getName()).isEqualTo(TEST_EMAIL);
            assertThat(upat.getCredentials()).isEqualTo(TEST_PASSWORD);

            verify(mockJwtIssuer).generateToken(successfulAuthentication);

            assertThat(authResponse).isNotNull();
            assertThat(authResponse.getAccessToken()).isEqualTo(TEST_JWT_TOKEN);
            assertThat(authResponse.getExpiresIn()).isEqualTo(TEST_EXPIRATION_SECONDS);
        }

        @Test
        @DisplayName("Невалидные креды -> должен пробросить AuthenticationException")
        void login_whenCredentialsAreInvalid_shouldPropagateException() {
            LoginRequest request = new LoginRequest(TEST_EMAIL, "wrongPassword");
            when(mockAuthenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatExceptionOfType(BadCredentialsException.class)
                    .isThrownBy(() -> authService.login(request));

            verify(mockJwtIssuer, never()).generateToken(any(Authentication.class));
        }
    }
}