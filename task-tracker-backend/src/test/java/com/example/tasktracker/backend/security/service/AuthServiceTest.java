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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Mock // Мокируем self-инъекцию
    private AuthService self; // Это поле будет использоваться в authService

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
    private static final Long TEST_EXPIRATION_SECONDS = TEST_EXPIRATION_MS / 1000;
    private static final Long SAVED_USER_ID = 1L;


    @BeforeEach
    void setUp() {
        // Важно: authService уже создан через @InjectMocks, и поле self в нем
        // будет указывать на наш @Mock AuthService self.
        // Если бы мы создавали authService вручную, нам бы пришлось передать этот мок в конструктор.
    }

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
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, "differentPassword");
        assertThatExceptionOfType(PasswordMismatchException.class)
                .isThrownBy(() -> authService.register(request));
        verifyNoInteractions(mockUserRepository, mockPasswordEncoder, mockJwtIssuer, self);
    }

    @Test
    @DisplayName("register: Пользователь с таким email уже существует (проверка перед сохранением) -> должен выбросить UserAlreadyExistsException")
    void register_whenUserEmailAlreadyExistsBeforeSave_shouldThrowUserAlreadyExistsException() {
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);
        when(mockUserRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

        assertThatExceptionOfType(UserAlreadyExistsException.class)
                .isThrownBy(() -> authService.register(request))
                .satisfies(ex -> assertThat(ex.getEmail()).isEqualTo(TEST_EMAIL));

        verify(mockUserRepository).existsByEmail(TEST_EMAIL);
        verifyNoInteractions(mockPasswordEncoder, mockJwtIssuer, self);
        verify(mockUserRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    @DisplayName("register: Валидный запрос -> должен сохранить пользователя, сгенерировать токен и вернуть AuthResponse")
    void register_whenRequestIsValid_shouldSaveUserEncodePasswordAndReturnAuthResponseWithToken() {
        when(mockJwtProperties.getExpirationMs()).thenReturn(TEST_EXPIRATION_MS);
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);

        when(mockUserRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(mockPasswordEncoder.encode(TEST_PASSWORD)).thenReturn(TEST_HASHED_PASSWORD);

        // Мокируем userRepository.saveAndFlush()
        when(mockUserRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User userArg = invocation.getArgument(0);
            User userWithId = new User();
            userWithId.setId(SAVED_USER_ID);
            userWithId.setEmail(userArg.getEmail());
            userWithId.setPassword(userArg.getPassword());
            return userWithId;
        });

        when(mockJwtIssuer.generateToken(any(Authentication.class))).thenReturn(TEST_JWT_TOKEN);

        AuthResponse authResponse = authService.register(request);

        verify(mockUserRepository).existsByEmail(TEST_EMAIL);
        verify(mockPasswordEncoder).encode(TEST_PASSWORD);

        verify(mockUserRepository).saveAndFlush(userArgumentCaptor.capture());
        User capturedUserForSave = userArgumentCaptor.getValue();
        assertThat(capturedUserForSave.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(capturedUserForSave.getPassword()).isEqualTo(TEST_HASHED_PASSWORD);
        assertThat(capturedUserForSave.getId()).isNull();

        verify(mockJwtIssuer).generateToken(authenticationArgumentCaptor.capture());
        Authentication generatedAuth = authenticationArgumentCaptor.getValue();
        assertThat(generatedAuth.getPrincipal()).isInstanceOf(AppUserDetails.class);
        AppUserDetails principalDetails = (AppUserDetails) generatedAuth.getPrincipal();
        assertThat(principalDetails.getId()).isEqualTo(SAVED_USER_ID);
        assertThat(principalDetails.getUsername()).isEqualTo(TEST_EMAIL);

        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getAccessToken()).isEqualTo(TEST_JWT_TOKEN);
        assertThat(authResponse.getTokenType()).isEqualTo("Bearer");
        assertThat(authResponse.getExpiresIn()).isEqualTo(TEST_EXPIRATION_SECONDS);

        verifyNoInteractions(self); // self не должен был вызываться в этом сценарии
    }

    @Test
    @DisplayName("register: Состояние гонки (DataIntegrityViolationException, затем email существует в новой транзакции) -> должен выбросить UserAlreadyExistsException")
    void register_whenRaceConditionOnSave_shouldThrowUserAlreadyExistsException() {
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);

        when(mockUserRepository.existsByEmail(TEST_EMAIL)).thenReturn(false); // Первичная проверка
        when(mockPasswordEncoder.encode(TEST_PASSWORD)).thenReturn(TEST_HASHED_PASSWORD);

        DataIntegrityViolationException dbException = new DataIntegrityViolationException("Simulated DB unique constraint violation");
        when(mockUserRepository.saveAndFlush(any(User.class))).thenThrow(dbException);

        // **Ключевое изменение:** Мокируем вызов self.existsByEmailInNewTransaction()
        when(self.existsByEmailInNewTransaction(TEST_EMAIL)).thenReturn(true);

        assertThatExceptionOfType(UserAlreadyExistsException.class)
                .isThrownBy(() -> authService.register(request))
                .satisfies(ex -> {
                    assertThat(ex.getEmail()).isEqualTo(TEST_EMAIL);
                    assertThat(ex.getCause()).isSameAs(dbException);
                });

        verify(mockUserRepository).existsByEmail(TEST_EMAIL); // Первичная проверка
        verify(mockPasswordEncoder).encode(TEST_PASSWORD);
        verify(mockUserRepository).saveAndFlush(any(User.class));
        verify(self).existsByEmailInNewTransaction(TEST_EMAIL); // Проверка вызова self
        verifyNoInteractions(mockJwtIssuer);
    }

    @Test
    @DisplayName("register: Неожиданная DataIntegrityViolationException (email НЕ существует в новой транзакции) -> должен выбросить IllegalStateException")
    void register_whenUnexpectedDataIntegrityViolationOnSave_shouldThrowIllegalStateException() {
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);

        when(mockUserRepository.existsByEmail(TEST_EMAIL)).thenReturn(false); // Первичная проверка
        when(mockPasswordEncoder.encode(TEST_PASSWORD)).thenReturn(TEST_HASHED_PASSWORD);

        DataIntegrityViolationException dbException = new DataIntegrityViolationException("Simulated other DB integrity violation");
        when(mockUserRepository.saveAndFlush(any(User.class))).thenThrow(dbException);

        // **Ключевое изменение:** Мокируем вызов self.existsByEmailInNewTransaction()
        when(self.existsByEmailInNewTransaction(TEST_EMAIL)).thenReturn(false); // Email НЕ существует

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> authService.register(request))
                .withMessage("Unexpected data integrity violation during user persistence")
                .withCause(dbException);

        verify(mockUserRepository).existsByEmail(TEST_EMAIL); // Первичная проверка
        verify(mockPasswordEncoder).encode(TEST_PASSWORD);
        verify(mockUserRepository).saveAndFlush(any(User.class));
        verify(self).existsByEmailInNewTransaction(TEST_EMAIL); // Проверка вызова self
        verifyNoInteractions(mockJwtIssuer);
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

        verifyNoInteractions(self); // self не должен был вызываться
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
        verifyNoInteractions(self); // self не должен был вызываться
    }
}