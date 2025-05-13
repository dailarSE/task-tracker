package com.example.tasktracker.backend.security.jwt;

import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.user.entity.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtValidatorTest {

    @Mock
    private JwtKeyService mockJwtKeyService;
    private Clock fixedClock;

    private JwtValidator jwtValidator;

    private JwtKeyService testRealJwtKeyService; // Для генерации токенов с известным ключом
    private JwtIssuer tokenGenerator;

    private static final String VALID_BASE64_KEY_32_BYTES = "bXlWZXJ5U2VjcmV0S2V5Rm9yVGFza1RyYWNrZXJBcHA=";
    private static final String ANOTHER_VALID_BASE64_KEY_32_BYTES = "YW5vdGhlclNlY3JldEtleUZvclRhc2tUcmFja2VyQXBw";
    public static final String DEFAULT_EMAIL_CLAIM_KEY = "email";
    public static final String DEFAULT_AUTHORITIES_CLAIM_KEY = "authorities";
    private static final long DEFAULT_EXPIRATION_MS = 3600000L; // 1 час
    private static final Instant NOW_INSTANT = Instant.parse("2025-01-01T12:00:00Z");
    private static final Long USER_ID = 1L;
    private static final String USER_EMAIL = "test@example.com";
    private static final String USER_PASSWORD_HASH_PLACEHOLDER = "hashedPassword"; // Для создания User

    private SecretKey secondaryTestSecretKey;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(NOW_INSTANT, ZoneOffset.UTC);

        JwtProperties testJwtProperties = new JwtProperties();
        testJwtProperties.setSecretKey(VALID_BASE64_KEY_32_BYTES);
        testJwtProperties.setExpirationMs(DEFAULT_EXPIRATION_MS);
        testJwtProperties.setEmailClaimKey(DEFAULT_EMAIL_CLAIM_KEY);
        testJwtProperties.setAuthoritiesClaimKey(DEFAULT_AUTHORITIES_CLAIM_KEY);

        testRealJwtKeyService = new JwtKeyService(testJwtProperties);
        SecretKey primaryTestSecretKey = testRealJwtKeyService.getSecretKey();

        tokenGenerator = new JwtIssuer(testJwtProperties, testRealJwtKeyService, fixedClock);

        when(mockJwtKeyService.getSecretKey()).thenReturn(primaryTestSecretKey);
        jwtValidator = new JwtValidator(mockJwtKeyService, fixedClock);

        JwtProperties otherProps = new JwtProperties();
        otherProps.setSecretKey(ANOTHER_VALID_BASE64_KEY_32_BYTES);
        secondaryTestSecretKey = (new JwtKeyService(otherProps)).getSecretKey();
    }

    @Test
    @DisplayName("Конструктор: JwtKeyService null -> должен выбросить NullPointerException")
    void constructor_whenJwtKeyServiceIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> new JwtValidator(null, fixedClock))
                .withMessageContaining("jwtKeyService is marked non-null but is null");
    }

    @Test
    @DisplayName("Конструктор: Clock null -> должен выбросить NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> new JwtValidator(mockJwtKeyService, null))
                .withMessageContaining("clock is marked non-null but is null");
    }

    @Test
    @DisplayName("isValid: Для валидного токена (без authorities) -> должен вернуть true")
    void isValid_whenTokenIsValidAndNoAuthorities_shouldReturnTrue() {
        // Arrange
        AppUserDetails userDetails = createAppUserDetailsWithUser(USER_ID, USER_EMAIL, USER_PASSWORD_HASH_PLACEHOLDER);
        Authentication auth = createAuthentication(userDetails);
        String validToken = tokenGenerator.generateToken(auth);

        // Act
        boolean isValid = jwtValidator.isValid(validToken);

        // Assert
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("extractValidClaims: Для валидного токена (без authorities) -> должен вернуть Optional с Claims")
    void extractValidClaims_whenTokenIsValidAndNoAuthorities_shouldReturnClaims() {
        // Arrange
        AppUserDetails userDetails = createAppUserDetailsWithUser(USER_ID, USER_EMAIL, USER_PASSWORD_HASH_PLACEHOLDER);
        Authentication auth = createAuthentication(userDetails);
        String validToken = tokenGenerator.generateToken(auth);

        // Act
        Optional<Claims> claimsOptional = jwtValidator.extractValidClaims(validToken);

        // Assert
        assertThat(claimsOptional).isPresent();
        Claims claims = claimsOptional.get();
        assertThat(claims.getSubject()).isEqualTo(String.valueOf(USER_ID));
        assertThat(claims.get(DEFAULT_EMAIL_CLAIM_KEY, String.class)).isEqualTo(USER_EMAIL);
        assertThat(claims.get(DEFAULT_AUTHORITIES_CLAIM_KEY, String.class)).isEmpty();

        Instant expectedIssuedAt = NOW_INSTANT;
        Instant expectedExpiration = NOW_INSTANT.plusMillis(DEFAULT_EXPIRATION_MS);
        assertThat(claims.getIssuedAt()).isEqualTo(Date.from(expectedIssuedAt));
        assertThat(claims.getExpiration()).isEqualTo(Date.from(expectedExpiration));
    }

    @DisplayName("isValid/extractValidClaims: Для null токена -> должны вернуть false/empty")
    @Test
    void validationMethods_whenTokenIsNull_shouldReturnFalseAndEmptyOptional() {
        // Act
        boolean isValid = jwtValidator.isValid(null);
        Optional<Claims> claimsOptional = jwtValidator.extractValidClaims(null);

        // Assert
        assertThat(isValid).isFalse();
        assertThat(claimsOptional).isEmpty();
    }

    @DisplayName("isValid/extractValidClaims: Для blank токена -> должны вернуть false/empty")
    @ParameterizedTest(name = "Для blank токена: \"{0}\"")
    @ValueSource(strings = {"", " ", "   "})
    void validationMethods_whenTokenIsBlank_shouldReturnFalseAndEmptyOptional(String blankToken) {
        // Act
        boolean isValid = jwtValidator.isValid(blankToken);
        Optional<Claims> claimsOptional = jwtValidator.extractValidClaims(blankToken);

        // Assert
        assertThat(isValid).isFalse();
        assertThat(claimsOptional).isEmpty();
    }

    @Test
    @DisplayName("isValid/extractValidClaims: Токен с неверной подписью -> должны вернуть false/empty")
    void validationMethods_whenTokenHasInvalidSignature_shouldReturnFalseAndEmptyOptional() {
        // Arrange
        AppUserDetails userDetails = createAppUserDetailsWithUser(USER_ID, USER_EMAIL, USER_PASSWORD_HASH_PLACEHOLDER);
        Authentication auth = createAuthentication(userDetails);
        // Генерируем токен с primaryTestSecretKey
        String tokenGeneratedWithPrimaryKey = tokenGenerator.generateToken(auth);

        // Настраиваем JwtValidator на использование ДРУГОГО ключа для валидации
        when(mockJwtKeyService.getSecretKey()).thenReturn(secondaryTestSecretKey);
        // Пересоздаем jwtValidator, так как mockJwtKeyService изменился ПОСЛЕ его создания в setUp
        // Или лучше делать это в @BeforeEach для каждого теста, если ключ валидации будет меняться.
        // Пока сделаем так:
        JwtValidator validatorWithWrongKey = new JwtValidator(mockJwtKeyService, fixedClock);


        // Act
        boolean isValid = validatorWithWrongKey.isValid(tokenGeneratedWithPrimaryKey);
        Optional<Claims> claimsOptional = validatorWithWrongKey.extractValidClaims(tokenGeneratedWithPrimaryKey);

        // Assert
        assertThat(isValid).isFalse();
        assertThat(claimsOptional).isEmpty();
        //Опционально - проверить лог на SignatureException WARN
    }

    @DisplayName("isValid/extractValidClaims: Токен неверного формата (malformed) -> должны вернуть false/empty")
    @ParameterizedTest(name = "Для malformed токена: \"{0}\"")
    @ValueSource(strings = {
            "this.is.not.a.jwt",                 // Неверное количество частей или не Base64
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0", // Отсутствует подпись
            "header.payload.signature_but_invalid_chars_in_payload!@#" // Невалидные символы
    })
    void validationMethods_whenTokenIsMalformed_shouldReturnFalseAndEmptyOptional(String malformedToken) {
        // Act
        boolean isValid = jwtValidator.isValid(malformedToken);
        Optional<Claims> claimsOptional = jwtValidator.extractValidClaims(malformedToken);

        // Assert
        assertThat(isValid).isFalse();
        assertThat(claimsOptional).isEmpty();
        // Опционально - проверить лог на MalformedJwtException WARN
    }

    @Test
    @DisplayName("isValid/extractValidClaims: Истекший токен -> должны вернуть false/empty")
    void validationMethods_whenTokenIsExpired_shouldReturnFalseAndEmptyOptional() {
        // Arrange
        // Генерируем токен, который истек 1 секунду назад относительно fixedClock (NOW_INSTANT)
        // Для этого, при генерации используем Clock, смещенный в прошлое так, чтобы
        // NOW_INSTANT (время валидации) было уже ПОСЛЕ времени истечения токена.
        long expirationShortMs = 1000L; // 1 секунда жизни
        Instant generationTime = NOW_INSTANT.minusMillis(expirationShortMs + 1000L); // Генерируем так, чтобы он уже истек
        Clock generationClock = Clock.fixed(generationTime, ZoneOffset.UTC);

        JwtProperties shortLivedProps = new JwtProperties();
        shortLivedProps.setSecretKey(VALID_BASE64_KEY_32_BYTES);
        shortLivedProps.setExpirationMs(expirationShortMs); // Короткое время жизни

        // Используем testRealJwtKeyService, так как он создан с VALID_BASE64_KEY_32_BYTES
        JwtIssuer shortLivedTokenGenerator = new JwtIssuer(shortLivedProps, testRealJwtKeyService, generationClock);

        AppUserDetails userDetails = createAppUserDetailsWithUser(USER_ID, USER_EMAIL, USER_PASSWORD_HASH_PLACEHOLDER);
        Authentication auth = createAuthentication(userDetails);
        String expiredToken = shortLivedTokenGenerator.generateToken(auth);

        // Валидатор использует fixedClock (NOW_INSTANT), который наступил после истечения токена

        // Act
        boolean isValid = jwtValidator.isValid(expiredToken);
        Optional<Claims> claimsOptional = jwtValidator.extractValidClaims(expiredToken);

        // Assert
        assertThat(isValid).isFalse();
        assertThat(claimsOptional).isEmpty();
        // Опционально - проверить лог на ExpiredJwtException. DEBUG level!
    }


    private AppUserDetails createAppUserDetailsWithUser(Long id, String email, String password) {
        User mockUser = Mockito.mock(User.class);
        when(mockUser.getId()).thenReturn(id);
        when(mockUser.getEmail()).thenReturn(email);
        when(mockUser.getPassword()).thenReturn(password);

        return new AppUserDetails(mockUser);
    }

    private Authentication createAuthentication(AppUserDetails userDetails) {
        return new TestingAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}