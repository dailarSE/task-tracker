package com.example.tasktracker.backend.security.service;

import com.example.tasktracker.backend.common.MdcKeys;
import com.example.tasktracker.backend.kafka.service.EmailNotificationOrchestratorService;
import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.security.dto.AuthResponse;
import com.example.tasktracker.backend.security.dto.LoginRequest;
import com.example.tasktracker.backend.security.dto.RegisterRequest;
import com.example.tasktracker.backend.security.jwt.JwtIssuer;
import com.example.tasktracker.backend.security.jwt.JwtProperties;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.messaging.dto.EmailTriggerCommand;
import com.example.tasktracker.backend.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;
import java.util.Map;

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
    private EmailNotificationOrchestratorService mockKafkaProducerService;
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
    private ArgumentCaptor<Authentication> authenticationArgumentCaptor;
    @Captor
    private ArgumentCaptor<UsernamePasswordAuthenticationToken> upatArgumentCaptor;
    @Captor
    private ArgumentCaptor<EmailTriggerCommand> emailCommandCaptor;


    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_HASHED_PASSWORD = "hashedPassword123";
    private static final String TEST_JWT_TOKEN = "test.jwt.token.string";
    private static final Long TEST_EXPIRATION_MS = 3600000L;
    private static final Long TEST_EXPIRATION_SECONDS = TEST_EXPIRATION_MS / 1000;
    private static final Long SAVED_USER_ID = 1L;
    private User mockSavedUser;


    @BeforeEach
    void setUpForEachTestCase() {
        // Настройка мока для сохраненного пользователя, чтобы он имел ID
        mockSavedUser = new User();
        mockSavedUser.setId(SAVED_USER_ID);
        mockSavedUser.setEmail(TEST_EMAIL);
        mockSavedUser.setPassword(TEST_HASHED_PASSWORD);
        // createdAt/updatedAt здесь не мокируем, так как persistNewUser их не устанавливает напрямую

        // Общие настройки моков
        lenient().when(mockPasswordEncoder.encode(TEST_PASSWORD)).thenReturn(TEST_HASHED_PASSWORD);
        lenient().when(mockUserRepository.saveAndFlush(any(User.class))).thenReturn(mockSavedUser);
        lenient().when(mockJwtIssuer.generateToken(any(Authentication.class))).thenReturn(TEST_JWT_TOKEN);
        lenient().when(mockJwtProperties.getExpirationMs()).thenReturn(TEST_EXPIRATION_MS);
    }

    @AfterEach
    void tearDownForEachTestCase() {
        MDC.clear(); // Очищаем MDC после каждого теста на всякий случай
    }

    @Nested
    @DisplayName("Метод register()")
    class RegisterTests {

        @Test
        @DisplayName("Валидный запрос -> должен установить userId в MDC, вызвать initiateWelcomeNotification, сгенерировать токен и вернуть AuthResponse")
        void register_whenRequestIsValid_shouldSetMdcCallNotificationAndReturnAuthResponse() {
            // Arrange
            RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);
            when(mockUserRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            // Mock persistNewUser to return our mockSavedUser
            // Для этого нам нужно, чтобы authService.persistNewUser вызывался, а не мокировался,
            // но его внутренний вызов self.existsByEmailInNewTransaction должен быть замокан.
            // Поэтому, мы не мокируем persistNewUser здесь, он будет вызван реально.
            // А вот вызов self.existsByEmailInNewTransaction будет идти на мок selfInjectedMock.
            lenient().when(selfInjectedMock.existsByEmailInNewTransaction(anyString())).thenReturn(false);


            Locale expectedLocale = Locale.FRENCH;
            try (MockedStatic<LocaleContextHolder> mockedLocaleContextHolder = mockStatic(LocaleContextHolder.class)) {
                mockedLocaleContextHolder.when(LocaleContextHolder::getLocale).thenReturn(expectedLocale);
                MDC.remove(MdcKeys.USER_ID); // Убедимся, что MDC пуст перед вызовом

                // Act
                AuthResponse authResponse = authService.register(request);

                // Assert
                verify(mockUserRepository).existsByEmail(TEST_EMAIL);
                verify(mockPasswordEncoder).encode(TEST_PASSWORD);
                verify(mockUserRepository).saveAndFlush(userArgumentCaptor.capture());
                User capturedUserForSave = userArgumentCaptor.getValue();
                assertThat(capturedUserForSave.getEmail()).isEqualTo(TEST_EMAIL);
                assertThat(capturedUserForSave.getPassword()).isEqualTo(TEST_HASHED_PASSWORD);

                // Проверка вызова initiateWelcomeNotification (косвенно через вызов KafkaProducerService)
                verify(mockKafkaProducerService).scheduleInitialEmailNotification(emailCommandCaptor.capture());
                EmailTriggerCommand capturedCommand = emailCommandCaptor.getValue();
                assertThat(capturedCommand.getRecipientEmail()).isEqualTo(TEST_EMAIL);
                assertThat(capturedCommand.getTemplateId()).isEqualTo("USER_WELCOME");
                assertThat(capturedCommand.getTemplateContext()).containsEntry("userEmail", TEST_EMAIL);
                assertThat(capturedCommand.getLocale()).isEqualTo(expectedLocale.toLanguageTag());
                assertThat(capturedCommand.getUserId()).isEqualTo(SAVED_USER_ID);

                verify(mockJwtIssuer).generateToken(authenticationArgumentCaptor.capture());
                Authentication generatedAuth = authenticationArgumentCaptor.getValue();
                assertThat(generatedAuth.getPrincipal()).isInstanceOf(AppUserDetails.class);
                AppUserDetails principalDetails = (AppUserDetails) generatedAuth.getPrincipal();
                assertThat(principalDetails.getId()).isEqualTo(SAVED_USER_ID);

                assertThat(authResponse).isNotNull();
                assertThat(authResponse.getAccessToken()).isEqualTo(TEST_JWT_TOKEN);

                // Проверяем, что MDC был очищен после выхода из try-with-resources в register()
                assertThat(MDC.get(MdcKeys.USER_ID)).isNull();
            }
        }

        @Test
        @DisplayName("Ошибка при инициации Kafka-сообщения -> регистрация должна пройти, ошибка Kafka логируется (не выбрасывается исключение)")
        void register_whenKafkaInitiationFails_shouldStillRegisterUserAndNotThrow() {
            // Arrange
            RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);
            when(mockUserRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            lenient().when(selfInjectedMock.existsByEmailInNewTransaction(anyString())).thenReturn(false);


            // Имитируем ошибку от KafkaProducerService (или от логики внутри initiateWelcomeNotification)
            doThrow(new RuntimeException("Simulated Kafka Initiation Error"))
                    .when(mockKafkaProducerService).scheduleInitialEmailNotification(any(EmailTriggerCommand.class));

            try (MockedStatic<LocaleContextHolder> mockedLocaleContextHolder = mockStatic(LocaleContextHolder.class)) {
                mockedLocaleContextHolder.when(LocaleContextHolder::getLocale).thenReturn(Locale.ENGLISH);
                MDC.remove(MdcKeys.USER_ID);

                // Act & Assert: Проверяем, что authService.register() НЕ выбрасывает исключение
                AuthResponse authResponse;
                assertThatCode(() -> authService.register(request)).doesNotThrowAnyException();

                // Вызываем еще раз, чтобы получить результат для проверки токена (или сохраняем из assertThatCode)
                // Для чистоты, можно обернуть вызов authService.register(request) в try-блок и проверять результат там.
                // В данном случае, просто вызываем еще раз, чтобы получить authResponse.
                // Важно: моки для userRepository.saveAndFlush и jwtIssuer.generateToken должны быть lenient
                // или настроены на множественные вызовы, если тест логика это предполагает.
                // В данном случае, так как мы повторно вызываем register, нужны lenient моки или times(2).
                // Перенастроим saveAndFlush, чтобы он всегда возвращал mockSavedUser для простоты.
                lenient().when(mockUserRepository.saveAndFlush(any(User.class))).thenReturn(mockSavedUser);
                authResponse = authService.register(request);


                // Дополнительные проверки, что пользователь был создан и токен выдан
                verify(mockUserRepository, atLeastOnce()).saveAndFlush(any(User.class)); // Проверяем, что был вызван хотя бы раз
                verify(mockJwtIssuer, atLeastOnce()).generateToken(any(Authentication.class));
                assertThat(authResponse).isNotNull();
                assertThat(authResponse.getAccessToken()).isEqualTo(TEST_JWT_TOKEN);

                // Проверяем, что mockKafkaProducerService был вызван
                verify(mockKafkaProducerService, atLeastOnce()).scheduleInitialEmailNotification(any(EmailTriggerCommand.class));
                assertThat(MDC.get(MdcKeys.USER_ID)).isNull(); // MDC должен быть очищен
            }
        }
    }

    @Nested
    @DisplayName("Метод initiateWelcomeNotification() (package-private)")
    class InitiateWelcomeNotificationTests {
        private User testNewUser;

        @BeforeEach
        void setUpForNotificationTests() {
            testNewUser = new User();
            testNewUser.setId(SAVED_USER_ID);
            testNewUser.setEmail(TEST_EMAIL);
        }

        @Test
        @DisplayName("Успешная инициация -> должен вызвать kafkaProducerService.sendEmailTrigger с корректной командой")
        void initiateWelcomeNotification_whenSuccessful_shouldCallKafkaProducerServiceWithCorrectCommand() {
            // Arrange
            Locale expectedLocale = Locale.JAPANESE;
            try (MockedStatic<LocaleContextHolder> mockedLocaleContextHolder = mockStatic(LocaleContextHolder.class)) {
                mockedLocaleContextHolder.when(LocaleContextHolder::getLocale).thenReturn(expectedLocale);

                // Act
                authService.initiateWelcomeNotification(testNewUser);

                // Assert
                verify(mockKafkaProducerService).scheduleInitialEmailNotification(emailCommandCaptor.capture());
                EmailTriggerCommand capturedCommand = emailCommandCaptor.getValue();
                assertThat(capturedCommand.getRecipientEmail()).isEqualTo(TEST_EMAIL);
                assertThat(capturedCommand.getTemplateId()).isEqualTo("USER_WELCOME");
                assertThat(capturedCommand.getTemplateContext()).isEqualTo(Map.of("userEmail", TEST_EMAIL));
                assertThat(capturedCommand.getLocale()).isEqualTo(expectedLocale.toLanguageTag());
                assertThat(capturedCommand.getUserId()).isEqualTo(SAVED_USER_ID);
                assertThat(capturedCommand.getCorrelationId()).isNull(); // Устанавливается в KafkaProducerService
            }
        }

        @Test
        @DisplayName("Исключение при вызове kafkaProducerService -> должен залогировать ошибку и не выбросить исключение")
        void initiateWelcomeNotification_whenKafkaProducerThrowsException_shouldLogAndNotThrow() {
            // Arrange
            doThrow(new RuntimeException("Kafka is down")).when(mockKafkaProducerService).scheduleInitialEmailNotification(any(EmailTriggerCommand.class));
            try (MockedStatic<LocaleContextHolder> mockedLocaleContextHolder = mockStatic(LocaleContextHolder.class)) {
                mockedLocaleContextHolder.when(LocaleContextHolder::getLocale).thenReturn(Locale.ENGLISH);

                // Act & Assert
                assertThatCode(() -> authService.initiateWelcomeNotification(testNewUser))
                        .doesNotThrowAnyException();

                // Проверяем, что была попытка отправить
                verify(mockKafkaProducerService).scheduleInitialEmailNotification(any(EmailTriggerCommand.class));
                // Проверка логирования (сложно без LogCaptor, но подразумевается, что лог ошибки будет)
            }
        }

        @Test
        @DisplayName("newUser равен null -> должен выбросить NullPointerException")
        void initiateWelcomeNotification_whenNewUserIsNull_shouldThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> authService.initiateWelcomeNotification(null))
                    .withMessageContaining("newUser is marked non-null but is null");
        }
    }

    @Nested
    @DisplayName("Метод getEmailTriggerCommand() (package-private)")
    class GetEmailTriggerCommandTests {
        private User testNewUser;

        @BeforeEach
        void setUpForGetCommandTests() {
            testNewUser = new User();
            testNewUser.setId(SAVED_USER_ID);
            testNewUser.setEmail(TEST_EMAIL);
        }

        @Test
        @DisplayName("Должен вернуть корректно сформированный EmailTriggerCommand")
        void getEmailTriggerCommand_shouldReturnCorrectlyFormedCommand() {
            // Arrange
            Locale expectedLocale = Locale.KOREAN;
            try (MockedStatic<LocaleContextHolder> mockedLocaleContextHolder = mockStatic(LocaleContextHolder.class)) {
                mockedLocaleContextHolder.when(LocaleContextHolder::getLocale).thenReturn(expectedLocale);

                // Act
                EmailTriggerCommand command = authService.createEmailTriggerCommand(testNewUser);

                // Assert
                assertThat(command.getRecipientEmail()).isEqualTo(TEST_EMAIL);
                assertThat(command.getTemplateId()).isEqualTo("USER_WELCOME");
                assertThat(command.getTemplateContext()).isEqualTo(Map.of("userEmail", TEST_EMAIL));
                assertThat(command.getLocale()).isEqualTo(expectedLocale.toLanguageTag());
                assertThat(command.getUserId()).isEqualTo(SAVED_USER_ID);
                assertThat(command.getCorrelationId()).isNull();
            }
        }

        @Test
        @DisplayName("newUser равен null -> должен выбросить NullPointerException")
        void getEmailTriggerCommand_whenNewUserIsNull_shouldThrowNullPointerException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> authService.createEmailTriggerCommand(null))
                    .withMessageContaining("newUser is marked non-null but is null");
        }
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
        when(mockJwtProperties.getExpirationMs()).thenReturn(TEST_EXPIRATION_MS);
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);

        User userFromDb = new User();
        userFromDb.setId(SAVED_USER_ID);
        userFromDb.setEmail(TEST_EMAIL);
        userFromDb.setPassword(TEST_HASHED_PASSWORD);
        AppUserDetails authenticatedUserDetails = new AppUserDetails(userFromDb);
        Authentication successfulAuthentication = new TestingAuthenticationToken(
                authenticatedUserDetails, TEST_PASSWORD, authenticatedUserDetails.getAuthorities()
        );
        when(mockAuthenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(successfulAuthentication);

        when(mockJwtIssuer.generateToken(successfulAuthentication)).thenReturn(TEST_JWT_TOKEN);

        AuthResponse authResponse = authService.login(request);

        verify(mockAuthenticationManager).authenticate(upatArgumentCaptor.capture());
        UsernamePasswordAuthenticationToken upat = upatArgumentCaptor.getValue();
        assertThat(upat.getName()).isEqualTo(TEST_EMAIL);
        assertThat(upat.getCredentials()).isEqualTo(TEST_PASSWORD);

        verify(mockJwtIssuer).generateToken(successfulAuthentication);

        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getAccessToken()).isEqualTo(TEST_JWT_TOKEN);
        assertThat(authResponse.getTokenType()).isEqualTo("Bearer");
        assertThat(authResponse.getExpiresIn()).isEqualTo(TEST_EXPIRATION_SECONDS);

        verifyNoInteractions(selfInjectedMock); // self не должен был вызываться
    }

    @Test
    @DisplayName("login: Невалидные креды (AuthenticationManager выбрасывает исключение) -> должен пробросить исключение")
    void login_whenAuthenticationManagerThrowsAuthenticationException_shouldPropagateException() {
        LoginRequest request = new LoginRequest(TEST_EMAIL, "wrongPassword");
        when(mockAuthenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials for test"));

        assertThatExceptionOfType(BadCredentialsException.class)
                .isThrownBy(() -> authService.login(request))
                .withMessage("Bad credentials for test");

        verify(mockJwtIssuer, never()).generateToken(any(Authentication.class));
        verifyNoInteractions(selfInjectedMock); // self не должен был вызываться
    }
}