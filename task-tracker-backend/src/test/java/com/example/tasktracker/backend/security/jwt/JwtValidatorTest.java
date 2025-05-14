package com.example.tasktracker.backend.security.jwt;

import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
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
        assertThatNullPointerException().isThrownBy(() -> new JwtValidator(null, fixedClock));
    }

    @Test
    @DisplayName("Конструктор: Clock null -> должен выбросить NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException().isThrownBy(() -> new JwtValidator(mockJwtKeyService, null));
    }

    // --- Тесты для validateAndParseToken (основной метод) ---
    @Test
    @DisplayName("validateAndParseToken: Для валидного токена -> должен вернуть успешный JwtValidationResult с JwsClaims")
    void validateAndParseToken_whenTokenIsValid_shouldReturnSuccessResultWithJwsClaims() {
        AppUserDetails userDetails = createAppUserDetailsWithUser(USER_ID, USER_EMAIL, USER_PASSWORD_HASH_PLACEHOLDER);
        Authentication auth = createAuthentication(userDetails);
        String validToken = tokenGenerator.generateToken(auth);

        JwtValidationResult result = jwtValidator.validateAndParseToken(validToken);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getErrorType()).isNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getJwsClaimsOptional()).isPresent();
        Jws<Claims> jwsClaims = result.getJwsClaimsOptional().get();
        assertThat(jwsClaims.getPayload().getSubject()).isEqualTo(String.valueOf(USER_ID));
        assertThat(jwsClaims.getPayload().get(DEFAULT_EMAIL_CLAIM_KEY, String.class)).isEqualTo(USER_EMAIL);
    }

    @DisplayName("validateAndParseToken: Для null или blank токена -> должен вернуть JwtValidationResult с ошибкой EMPTY_OR_ILLEGAL_ARGUMENT")
    @ParameterizedTest(name = "Для токена: \"{0}\"")
    @ValueSource(strings = {"", " ", "   "}) // null проверяется отдельно
    void validateAndParseToken_whenTokenIsBlank_shouldReturnFailureResultWithEmptyOrIllegalArgument(String blankToken) {
        JwtValidationResult result = jwtValidator.validateAndParseToken(blankToken);
        assertFailureResult(result, JwtErrorType.EMPTY_OR_ILLEGAL_ARGUMENT, "Token is null or blank");
    }

    @Test
    @DisplayName("validateAndParseToken: Для null токена -> должен вернуть JwtValidationResult с ошибкой EMPTY_OR_ILLEGAL_ARGUMENT")
    void validateAndParseToken_whenTokenIsNull_shouldReturnFailureResultWithEmptyOrIllegalArgument() {
        JwtValidationResult result = jwtValidator.validateAndParseToken(null);
        assertFailureResult(result, JwtErrorType.EMPTY_OR_ILLEGAL_ARGUMENT, "Token is null or blank");
    }


    @Test
    @DisplayName("validateAndParseToken: Токен с неверной подписью -> должен вернуть JwtValidationResult с ошибкой INVALID_SIGNATURE")
    void validateAndParseToken_whenTokenHasInvalidSignature_shouldReturnFailureResultWithInvalidSignature() {
        // Arrange
        AppUserDetails userDetails = createAppUserDetailsWithUser(USER_ID, USER_EMAIL, USER_PASSWORD_HASH_PLACEHOLDER);
        Authentication auth = createAuthentication(userDetails);
        // Генерируем токен с primaryTestSecretKey (через tokenGenerator)
        String tokenGeneratedWithPrimaryKey = tokenGenerator.generateToken(auth);

        // Создаем новый мок JwtKeyService для этого теста, который вернет ДРУГОЙ ключ
        JwtKeyService keyServiceWithWrongKey = mock(JwtKeyService.class); // Локальный мок для этого теста
        when(keyServiceWithWrongKey.getSecretKey()).thenReturn(secondaryTestSecretKey); // secondaryTestSecretKey - это ДРУГОЙ ключ

        // Создаем JwtValidator, который будет использовать этот "неправильный" ключ
        JwtValidator validatorWithWrongKey = new JwtValidator(keyServiceWithWrongKey, fixedClock);

        // Act
        JwtValidationResult result = validatorWithWrongKey.validateAndParseToken(tokenGeneratedWithPrimaryKey);

        // Assert
        assertFailureResult(result, JwtErrorType.INVALID_SIGNATURE, "Invalid JWT signature.");
    }

    @DisplayName("validateAndParseToken: Токен неверного формата (malformed) -> должен вернуть JwtValidationResult с ошибкой MALFORMED")
    @ParameterizedTest(name = "Для malformed токена: \"{0}\"")
    @ValueSource(strings = {"this.is.not.a.jwt", "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0"})
    void validateAndParseToken_whenTokenIsMalformed_shouldReturnFailureResultWithMalformed(String malformedToken) {
        JwtValidationResult result = jwtValidator.validateAndParseToken(malformedToken);
        assertFailureResult(result, JwtErrorType.MALFORMED, "Invalid JWT token format.");
    }

    @Test
    @DisplayName("validateAndParseToken: Истекший токен -> должен вернуть JwtValidationResult с ошибкой EXPIRED")
    void validateAndParseToken_whenTokenIsExpired_shouldReturnFailureResultWithExpired() {
        long expirationShortMs = -1000L; // Истек 1 секунду назад от времени генерации
        Instant generationTime = NOW_INSTANT; // Генерируем "сейчас" (по fixedClock)
        Clock generationClock = Clock.fixed(generationTime, ZoneOffset.UTC);

        JwtProperties shortLivedProps = new JwtProperties();
        shortLivedProps.setSecretKey(VALID_BASE64_KEY_32_BYTES);
        shortLivedProps.setExpirationMs(expirationShortMs); // Отрицательное время жизни = истек в момент создания
        shortLivedProps.setEmailClaimKey("email");
        shortLivedProps.setAuthoritiesClaimKey("authorities");


        JwtIssuer shortLivedTokenGenerator = new JwtIssuer(shortLivedProps, testRealJwtKeyService, generationClock);

        AppUserDetails userDetails = createAppUserDetailsWithUser(USER_ID, USER_EMAIL, USER_PASSWORD_HASH_PLACEHOLDER);
        Authentication auth = createAuthentication(userDetails);
        String expiredToken = shortLivedTokenGenerator.generateToken(auth);

        // Валидатор использует fixedClock (NOW_INSTANT), который такой же, как generationTime,
        // но токен уже создан как истекший.
        JwtValidationResult result = jwtValidator.validateAndParseToken(expiredToken);
        assertFailureResult(result, JwtErrorType.EXPIRED, "JWT token is expired.");
    }

    // --- Тесты для isValid (просто обертка) ---
    @Test
    @DisplayName("isValid: Для валидного токена -> должен вернуть true")
    void isValid_whenTokenIsValid_shouldReturnTrue() {
        AppUserDetails userDetails = createAppUserDetailsWithUser(USER_ID, USER_EMAIL, USER_PASSWORD_HASH_PLACEHOLDER);
        Authentication auth = createAuthentication(userDetails);
        String validToken = tokenGenerator.generateToken(auth);
        assertThat(jwtValidator.isValid(validToken)).isTrue();
    }

    @Test
    @DisplayName("isValid: Для невалидного (истекшего) токена -> должен вернуть false")
    void isValid_whenTokenIsExpired_shouldReturnFalse() {
        // Используем ту же логику генерации истекшего токена, что и в тесте для validateAndParseToken
        JwtProperties expiredProps = new JwtProperties();
        expiredProps.setSecretKey(VALID_BASE64_KEY_32_BYTES);
        expiredProps.setExpirationMs(-1000L); // Истекший
        expiredProps.setEmailClaimKey("email");
        expiredProps.setAuthoritiesClaimKey("authorities");
        JwtIssuer expiredTokenGenerator = new JwtIssuer(expiredProps, testRealJwtKeyService, fixedClock);
        String expiredToken = expiredTokenGenerator.generateToken(createAuthentication(createAppUserDetailsWithUser(USER_ID, USER_EMAIL, USER_PASSWORD_HASH_PLACEHOLDER)));

        assertThat(jwtValidator.isValid(expiredToken)).isFalse();
    }


    // --- Тесты для extractValidClaims (просто обертка) ---
    @Test
    @DisplayName("extractValidClaims: Для валидного токена -> должен вернуть Optional с Claims")
    void extractValidClaims_whenTokenIsValid_shouldReturnClaims() {
        AppUserDetails userDetails = createAppUserDetailsWithUser(USER_ID, USER_EMAIL, USER_PASSWORD_HASH_PLACEHOLDER);
        Authentication auth = createAuthentication(userDetails);
        String validToken = tokenGenerator.generateToken(auth);

        Optional<Claims> claimsOptional = jwtValidator.extractValidClaims(validToken);
        assertThat(claimsOptional).isPresent();
        assertThat(claimsOptional.get().getSubject()).isEqualTo(String.valueOf(USER_ID));
    }

    @Test
    @DisplayName("extractValidClaims: Для невалидного (malformed) токена -> должен вернуть пустой Optional")
    void extractValidClaims_whenTokenIsMalformed_shouldReturnEmptyOptional() {
        String malformedToken = "not.a.jwt";
        assertThat(jwtValidator.extractValidClaims(malformedToken)).isEmpty();
    }


    // --- Тесты для truncateTokenForLogging (публичный метод) ---
    @Test
    @DisplayName("truncateTokenForLogging: null токен -> должен вернуть '[NULL_TOKEN]'")
    void truncateTokenForLogging_whenTokenIsNull_shouldReturnNullMarker() {
        assertThat(jwtValidator.truncateTokenForLogging(null)).isEqualTo("[NULL_TOKEN]");
    }

    @DisplayName("truncateTokenForLogging: blank токен -> должен вернуть '[BLANK_TOKEN]'")
    @ParameterizedTest(name = "Для blank токена: \"{0}\"")
    @ValueSource(strings = {"", " ", "   "})
    void truncateTokenForLogging_whenTokenIsBlank_shouldReturnBlankTokenMarker(String blankToken) {
        assertThat(jwtValidator.truncateTokenForLogging(blankToken)).isEqualTo("[BLANK_TOKEN]");
    }

    @Test
    @DisplayName("truncateTokenForLogging: короткий токен -> должен вернуть маркер с длиной")
    void truncateTokenForLogging_whenTokenIsShort_shouldReturnShortTokenMarkerWithLength() {
        String shortToken = "12345";
        assertThat(jwtValidator.truncateTokenForLogging(shortToken)).isEqualTo("[SHORT_TOKEN_LEN:5]");

        String slightlyLongerButStillShort = "123456789012345678"; // 18 символов, min для усечения 8+3+8=19
        assertThat(jwtValidator.truncateTokenForLogging(slightlyLongerButStillShort)).isEqualTo("[SHORT_TOKEN_LEN:18]");
    }

    @Test
    @DisplayName("truncateTokenForLogging: достаточно длинный токен -> должен вернуть усеченную строку")
    void truncateTokenForLogging_whenTokenIsLongEnough_shouldReturnTruncatedString() {
        String longToken = "abcdefghijklmnopqrstuvwxyz1234567890"; // 36 символов
        // prefixSuffixLength = 8, ellipsis = "..."
        String expected = "abcdefgh...34567890";
        assertThat(jwtValidator.truncateTokenForLogging(longToken)).isEqualTo(expected);
    }


    // Вспомогательный метод для проверки неуспешного JwtValidationResult
    private void assertFailureResult(JwtValidationResult result, JwtErrorType expectedType, String expectedMessagePart) {
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getJwsClaimsOptional()).isEmpty();
        assertThat(result.getErrorType()).isEqualTo(expectedType);
        assertThat(result.getErrorMessage()).isNotNull();
        if (expectedMessagePart != null) {
            assertThat(result.getErrorMessage()).containsIgnoringCase(expectedMessagePart);
        }
    }


    private AppUserDetails createAppUserDetailsWithUser(Long id, String email, String password) {
        User mockUser = mock(User.class);
        when(mockUser.getId()).thenReturn(id);
        when(mockUser.getEmail()).thenReturn(email);
        when(mockUser.getPassword()).thenReturn(password);

        return new AppUserDetails(mockUser);
    }

    private Authentication createAuthentication(AppUserDetails userDetails) {
        return new TestingAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}