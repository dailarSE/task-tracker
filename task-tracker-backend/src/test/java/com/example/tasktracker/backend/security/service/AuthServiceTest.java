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
                .isThrownBy(() -> authService.register(request));

        verifyNoInteractions(mockUserRepository, mockPasswordEncoder, mockJwtIssuer);
    }

    @Test
    @DisplayName("register: Пользователь с таким email уже существует -> должен выбросить UserAlreadyExistsException с корректным email")
    void register_whenUserEmailAlreadyExists_shouldThrowUserAlreadyExistsException() {
        // Arrange
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);
        when(mockUserRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

        // Act & Assert
        assertThatExceptionOfType(UserAlreadyExistsException.class)
                .isThrownBy(() -> authService.register(request))
                .satisfies(ex-> assertThat(ex.getEmail()).isEqualTo(TEST_EMAIL));

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

        // 1. Мокирование для первичной проверки existsByEmail в register()
        when(mockUserRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);

        // 2. Мокирование для passwordEncoder.encode() в register()
        when(mockPasswordEncoder.encode(TEST_PASSWORD)).thenReturn(TEST_HASHED_PASSWORD);

        // 3. Мокирование для userRepository.saveAndFlush() внутри persistNewUser()
        //    Это самая важная часть, так как persistNewUser теперь вызывается из register.
        when(mockUserRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User userArg = invocation.getArgument(0);
            // Проверяем, что User, переданный в saveAndFlush, имеет корректные email и хеш пароля
            assertThat(userArg.getEmail()).isEqualTo(TEST_EMAIL);
            assertThat(userArg.getPassword()).isEqualTo(TEST_HASHED_PASSWORD);

            // Имитируем, что БД присвоила ID и вернула сохраненного пользователя
            User userWithId = new User(); // Создаем новый экземпляр, как это сделал бы JPA
            userWithId.setId(SAVED_USER_ID);
            userWithId.setEmail(userArg.getEmail());
            userWithId.setPassword(userArg.getPassword());
            // createdAt/updatedAt будут null, так как мы не мокаем JPA Auditing здесь
            return userWithId;
        });

        // 4. Мокирование для jwtIssuer.generateToken() в register()
        when(mockJwtIssuer.generateToken(any(Authentication.class))).thenReturn(TEST_JWT_TOKEN);

        // Act
        AuthResponse authResponse = authService.register(request);

        // Assert
        // Проверки вызовов
        verify(mockUserRepository).existsByEmail(TEST_EMAIL); // Проверка из register()
        verify(mockPasswordEncoder).encode(TEST_PASSWORD);    // Проверка из register()

        // Проверяем, что saveAndFlush был вызван с правильным User объектом
        verify(mockUserRepository).saveAndFlush(userArgumentCaptor.capture());
        User capturedUserForSave = userArgumentCaptor.getValue();
        assertThat(capturedUserForSave.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(capturedUserForSave.getPassword()).isEqualTo(TEST_HASHED_PASSWORD);
        assertThat(capturedUserForSave.getId()).isNull(); // ID еще не должен быть установлен перед вызовом saveAndFlush

        verify(mockJwtIssuer).generateToken(authenticationArgumentCaptor.capture());
        Authentication generatedAuth = authenticationArgumentCaptor.getValue();
        assertThat(generatedAuth.getPrincipal()).isInstanceOf(AppUserDetails.class);
        AppUserDetails principalDetails = (AppUserDetails) generatedAuth.getPrincipal();
        assertThat(principalDetails.getId()).isEqualTo(SAVED_USER_ID); // ID из userWithId, возвращенного saveAndFlush
        assertThat(principalDetails.getUsername()).isEqualTo(TEST_EMAIL);

        // Проверки AuthResponse
        assertThat(authResponse).isNotNull();
        assertThat(authResponse.getAccessToken()).isEqualTo(TEST_JWT_TOKEN);
        assertThat(authResponse.getTokenType()).isEqualTo("Bearer");
        assertThat(authResponse.getExpiresIn()).isEqualTo(TEST_EXPIRATION_SECONDS); // Проверяем секунды
    }

    @Test
    @DisplayName("register: Состояние гонки при сохранении (email уже существует в БД после saveAndFlush) -> должен выбросить UserAlreadyExistsException")
    void register_whenRaceConditionOnSave_shouldThrowUserAlreadyExistsException() {
        // Arrange
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);

        // Настраиваем мок userRepository.existsByEmail(TEST_EMAIL) так, чтобы он:
        // 1. Первый раз вернул false (для проверки в методе register)
        // 2. Второй раз вернул true (для проверки в catch блоке метода persistNewUser)
        when(mockUserRepository.existsByEmail(TEST_EMAIL))
                .thenReturn(false)  // Для первого вызова
                .thenReturn(true);   // Для второго вызова

        when(mockPasswordEncoder.encode(TEST_PASSWORD)).thenReturn(TEST_HASHED_PASSWORD);

        DataIntegrityViolationException dbException = new DataIntegrityViolationException("Simulated DB unique constraint violation");
        when(mockUserRepository.saveAndFlush(any(User.class)))
                .thenThrow(dbException);

        // Act & Assert
        assertThatExceptionOfType(UserAlreadyExistsException.class)
                .isThrownBy(() -> authService.register(request))
                .satisfies(ex -> {
                    assertThat(ex.getEmail()).isEqualTo(TEST_EMAIL); // Это было на строке 194, ex.getEmail() не должен быть null
                    assertThat(ex.getCause()).isSameAs(dbException); // Проверяем, что причина - это наше dbException
                });

        // Проверяем, что все моки были вызваны как ожидалось
        verify(mockPasswordEncoder).encode(TEST_PASSWORD);
        verify(mockUserRepository).saveAndFlush(any(User.class)); // Попытка сохранения
        verify(mockUserRepository, times(2)).existsByEmail(TEST_EMAIL); // Должен быть вызван дважды
        verifyNoInteractions(mockJwtIssuer); // Токен не должен генерироваться
    }

    @Test
    @DisplayName("register: Неожиданная DataIntegrityViolationException при сохранении -> должен выбросить IllegalStateException")
    void register_whenUnexpectedDataIntegrityViolationOnSave_shouldThrowIllegalStateException() {
        // Arrange
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_PASSWORD);

        when(mockUserRepository.existsByEmail(TEST_EMAIL)).thenReturn(false); // Первичная проверка проходит
        when(mockPasswordEncoder.encode(TEST_PASSWORD)).thenReturn(TEST_HASHED_PASSWORD);

        DataIntegrityViolationException dbException = new DataIntegrityViolationException("Simulated other DB integrity violation");
        when(mockUserRepository.saveAndFlush(any(User.class)))
                .thenThrow(dbException);

        // Имитируем, что ПОСЛЕ исключения, email НЕ существует в БД (это НЕ гонка по email)
        // Важно, чтобы вторая проверка existsByEmail вернула false
        // Так как первая вернула false, и вторая (после исключения) тоже false.
        // Чтобы это сработало с одним моком, нужно использовать thenReturn с несколькими значениями:
        when(mockUserRepository.existsByEmail(TEST_EMAIL))
                .thenReturn(false) // Для первой проверки в register()
                .thenReturn(false); // Для второй проверки в persistNewUser() -> catch -> if

        // Act & Assert
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> authService.register(request))
                .withMessage("Unexpected data integrity violation during user persistence")
                .withCause(dbException);

        verify(mockUserRepository, times(2)).existsByEmail(TEST_EMAIL); // Две проверки
        verify(mockPasswordEncoder).encode(TEST_PASSWORD);
        verify(mockUserRepository).saveAndFlush(any(User.class));
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
        assertThat(authResponse.getExpiresIn()).isEqualTo(TEST_EXPIRATION_SECONDS);
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