package com.example.tasktracker.backend.security.jwt;

import com.example.tasktracker.backend.security.details.AppUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты для {@link JwtAuthenticationConverter}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // Lenient, так как mockJwtProperties используется не во всех тестах после setUp
class JwtAuthenticationConverterTest {

    @Mock
    private JwtProperties mockJwtProperties;

    private JwtAuthenticationConverter jwtAuthenticationConverter;

    // Используем константы, соответствующие тем, что используются в JwtAuthenticationConverter и JwtIssuer
    private static final String DEFAULT_EMAIL_CLAIM_KEY = "email"; // Это значение используется в setUp
    private static final String DEFAULT_AUTHORITIES_CLAIM_KEY = "authorities"; // Это значение используется в одном из тестов

    private static final Long TEST_USER_ID = 123L;
    private static final String TEST_USER_EMAIL = "test@example.com";
    private static final String TEST_RAW_JWT_TOKEN = "dummy.jwt.token";


    @BeforeEach
    void setUp() {
        // Настраиваем мок JwtProperties для возврата ключа email по умолчанию
        when(mockJwtProperties.getEmailClaimKey()).thenReturn(DEFAULT_EMAIL_CLAIM_KEY);
        jwtAuthenticationConverter = new JwtAuthenticationConverter(mockJwtProperties);
    }

    @Test
    @DisplayName("Конструктор: JwtProperties null -> должен выбросить NullPointerException")
    void constructor_whenJwtPropertiesIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> new JwtAuthenticationConverter(null))
                .withMessageContaining("jwtProperties is marked non-null but is null");
    }

    @Test
    @DisplayName("convert: Валидные Claims -> должен вернуть Authentication с AppUserDetails")
    void convert_whenClaimsAreValid_shouldReturnAuthenticatedTokenWithAppUserDetails() {
        // Arrange
        // Для этого теста также нужно настроить AuthoritiesClaimKey, если он используется в JwtAuthenticationConverter для создания AppUserDetails
        // В текущей версии он не используется AppUserDetails, но если бы использовался, то:
        when(mockJwtProperties.getAuthoritiesClaimKey()).thenReturn(DEFAULT_AUTHORITIES_CLAIM_KEY);

        Claims claims = Jwts.claims()
                .subject(String.valueOf(TEST_USER_ID)) // Используем константу
                .add(DEFAULT_EMAIL_CLAIM_KEY, TEST_USER_EMAIL) // Используем константу
                .add(DEFAULT_AUTHORITIES_CLAIM_KEY, "") // Для authorities, если ожидается
                .build();

        // Act
        Authentication authentication = jwtAuthenticationConverter.convert(claims, TEST_RAW_JWT_TOKEN);

        // Assert
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue(); // UsernamePasswordAuthenticationToken по умолчанию isAuth=true, если есть principal и authorities
        assertThat(authentication.getPrincipal()).isInstanceOf(AppUserDetails.class);

        AppUserDetails userPrincipal = (AppUserDetails) authentication.getPrincipal();
        assertThat(userPrincipal.getId()).isEqualTo(TEST_USER_ID);
        assertThat(userPrincipal.getUsername()).isEqualTo(TEST_USER_EMAIL);
        assertThat(userPrincipal.getAuthorities()).isNotNull().isEmpty(); // В текущей реализации AppUserDetails authorities пустые

        assertThat(authentication.getCredentials()).isEqualTo(TEST_RAW_JWT_TOKEN);
    }

    @Test
    @DisplayName("convert: Отсутствует Subject claim (null) -> должен выбросить IllegalArgumentException с сообщением о 'Missing sub claim'")
    void convert_whenSubjectClaimIsMissing_shouldThrowIllegalArgumentException() {
        // Arrange
        Claims claims = Jwts.claims() // Subject не установлен, claims.getSubject() вернет null
                .add(DEFAULT_EMAIL_CLAIM_KEY, TEST_USER_EMAIL)
                .build();

        // Act & Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtAuthenticationConverter.convert(claims, TEST_RAW_JWT_TOKEN))
                .withMessage("Missing 'sub' claim in JWT."); // Ожидаем новое сообщение из JwtAuthenticationConverter
    }

    @Test
    @DisplayName("convert: Subject claim не числовой -> должен выбросить IllegalArgumentException с сообщением о 'not a valid user ID'")
    void convert_whenSubjectClaimIsNotNumeric_shouldThrowIllegalArgumentException() {
        // Arrange
        String nonNumericSubject = "not-a-number";
        Claims claims = Jwts.claims()
                .subject(nonNumericSubject)
                .add(DEFAULT_EMAIL_CLAIM_KEY, TEST_USER_EMAIL)
                .build();

        // Act & Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtAuthenticationConverter.convert(claims, TEST_RAW_JWT_TOKEN))
                .withMessage("Invalid 'sub' claim in JWT: not a valid user ID.") // Проверяем точное сообщение
                .withCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("convert: Отсутствует Email claim -> должен выбросить IllegalArgumentException")
    void convert_whenEmailClaimIsMissing_shouldThrowIllegalArgumentException() {
        // Arrange
        Claims claims = Jwts.claims()
                .subject(String.valueOf(TEST_USER_ID))
                // Email claim не установлен
                .build();

        // Act & Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtAuthenticationConverter.convert(claims, TEST_RAW_JWT_TOKEN))
                // Сообщение формируется с использованием DEFAULT_EMAIL_CLAIM_KEY
                .withMessage("Missing or blank '" + DEFAULT_EMAIL_CLAIM_KEY + "' claim in JWT.");
    }

    @DisplayName("convert: Email claim пустой или blank -> должен выбросить IllegalArgumentException")
    @ParameterizedTest(name = "Для Email claim: \"{0}\"")
    @ValueSource(strings = {"", " ", "   "})
    void convert_whenEmailClaimIsBlank_shouldThrowIllegalArgumentException(String blankEmail) {
        // Arrange
        Claims claims = Jwts.claims()
                .subject(String.valueOf(TEST_USER_ID))
                .add(DEFAULT_EMAIL_CLAIM_KEY, blankEmail)
                .build();

        // Act & Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtAuthenticationConverter.convert(claims, TEST_RAW_JWT_TOKEN))
                .withMessage("Missing or blank '" + DEFAULT_EMAIL_CLAIM_KEY + "' claim in JWT.");
    }

    @Test
    @DisplayName("convert: Claims null -> должен выбросить NullPointerException (Lombok @NonNull)")
    void convert_whenClaimsIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> jwtAuthenticationConverter.convert(null, TEST_RAW_JWT_TOKEN))
                .withMessageContaining("claims is marked non-null but is null");
    }
}