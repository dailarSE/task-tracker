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
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationConverterTest {

    @Mock
    private JwtProperties mockJwtProperties;

    private JwtAuthenticationConverter jwtAuthenticationConverter;

    private static final String EMAIL_CLAIM_KEY_VALUE = "email";
    private static final String AUTHORITIES_CLAIM_KEY_VALUE = "authorities";

    @BeforeEach
    void setUp() {
        when(mockJwtProperties.getEmailClaimKey()).thenReturn(EMAIL_CLAIM_KEY_VALUE);
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
        when(mockJwtProperties.getAuthoritiesClaimKey()).thenReturn(AUTHORITIES_CLAIM_KEY_VALUE);
        // Arrange
        Claims claims = Jwts.claims()
                .subject("123")
                .add(EMAIL_CLAIM_KEY_VALUE, "test@example.com")
                .add(AUTHORITIES_CLAIM_KEY_VALUE, "")
                .build();
        String rawJwtToken = "dummy.jwt.token";

        // Act
        Authentication authentication = jwtAuthenticationConverter.convert(claims, rawJwtToken);

        // Assert
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isInstanceOf(AppUserDetails.class);

        AppUserDetails userPrincipal = (AppUserDetails) authentication.getPrincipal();
        assertThat(userPrincipal.getId()).isEqualTo(123L);
        assertThat(userPrincipal.getUsername()).isEqualTo("test@example.com");
        assertThat(userPrincipal.getAuthorities()).isNotNull().isEmpty();

        assertThat(authentication.getCredentials()).isEqualTo(rawJwtToken);
    }

    @Test
    @DisplayName("convert: Отсутствует Subject claim -> должен выбросить IllegalArgumentException")
    void convert_whenSubjectClaimIsMissing_shouldThrowIllegalArgumentException() {
        // Arrange
        Claims claims = Jwts.claims() // Subject не установлен
                .add(EMAIL_CLAIM_KEY_VALUE, "test@example.com")
                .build();

        // Act & Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtAuthenticationConverter.convert(claims, "token"))
                .withMessage("Invalid 'sub' claim in JWT: not a valid user ID format.");
    }

    @Test
    @DisplayName("convert: Subject claim не числовой -> должен выбросить IllegalArgumentException")
    void convert_whenSubjectClaimIsNotNumeric_shouldThrowIllegalArgumentException() {
        // Arrange
        Claims claims = Jwts.claims()
                .subject("not-a-number")
                .add(EMAIL_CLAIM_KEY_VALUE, "test@example.com")
                .build();

        // Act & Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtAuthenticationConverter.convert(claims, "token"))
                .withMessage("Invalid 'sub' claim in JWT: not a valid user ID format.")
                .withCauseInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("convert: Отсутствует Email claim -> должен выбросить IllegalArgumentException")
    void convert_whenEmailClaimIsMissing_shouldThrowIllegalArgumentException() {
        // Arrange
        Claims claims = Jwts.claims()
                .subject("123")
                // Email claim не установлен
                .build();

        // Act & Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtAuthenticationConverter.convert(claims, "token"))
                .withMessage("Missing or blank '" + EMAIL_CLAIM_KEY_VALUE + "' claim in JWT.");
    }

    @DisplayName("convert: Email claim пустой или blank -> должен выбросить IllegalArgumentException")
    @ParameterizedTest(name = "Для Email claim: \"{0}\"")
    @ValueSource(strings = {"", " ", "   "})
    void convert_whenEmailClaimIsBlank_shouldThrowIllegalArgumentException(String blankEmail) {
        // Arrange
        Claims claims = Jwts.claims()
                .subject("123")
                .add(EMAIL_CLAIM_KEY_VALUE, blankEmail)
                .build();

        // Act & Assert
        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtAuthenticationConverter.convert(claims, "token"))
                .withMessage("Missing or blank '" + EMAIL_CLAIM_KEY_VALUE + "' claim in JWT.");
    }

    @Test
    @DisplayName("convert: Claims null -> должен выбросить NullPointerException (Lombok @NonNull)")
    void convert_whenClaimsIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> jwtAuthenticationConverter.convert(null, "token"))
                .withMessageContaining("claims is marked non-null but is null");
    }
}