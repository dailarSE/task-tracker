package com.example.tasktracker.backend.security.jwt;

import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты для {@link JwtIssuer}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtIssuerTest {

    @Mock
    private JwtProperties mockJwtProperties;
    @Mock
    private JwtKeyService mockJwtKeyService;

    private Clock fixedClock;
    private JwtIssuer jwtIssuer;

    private static final String DEFAULT_EMAIL_CLAIM_KEY = "email";
    private static final String DEFAULT_AUTHORITIES_CLAIM_KEY = "authorities";
    private static final String CUSTOM_EMAIL_CLAIM_KEY = "user_email_custom";
    private static final String CUSTOM_AUTHORITIES_CLAIM_KEY = "user_roles_custom";

    private static final Long USER_ID = 1L;
    private static final String USER_EMAIL = "test@example.com";
    private static final String USER_PASSWORD_PLACEHOLDER = "password";
    private static final long EXPIRATION_MS = 3600000L; // 1 час
    private static final Instant FIXED_NOW = Instant.parse("2025-01-01T10:00:00Z");
    private SecretKey testSecretKey;

    @BeforeEach
    void setUp() {
        testSecretKey = Keys.hmacShaKeyFor("TestSecretKeyForJwtIssuerWhichIsSufficientlyLongForHS256".getBytes());
        when(mockJwtKeyService.getSecretKey()).thenReturn(testSecretKey);

        when(mockJwtProperties.getExpirationMs()).thenReturn(EXPIRATION_MS);
        when(mockJwtProperties.getEmailClaimKey()).thenReturn(DEFAULT_EMAIL_CLAIM_KEY);
        when(mockJwtProperties.getAuthoritiesClaimKey()).thenReturn(DEFAULT_AUTHORITIES_CLAIM_KEY);

        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

        jwtIssuer = new JwtIssuer(mockJwtProperties, mockJwtKeyService, fixedClock);
    }

    private AppUserDetails createAppUserDetails(Long id, String email) {
        User mockUser = Mockito.mock(User.class);
        when(mockUser.getId()).thenReturn(id);
        when(mockUser.getEmail()).thenReturn(email);
        when(mockUser.getPassword()).thenReturn(USER_PASSWORD_PLACEHOLDER);
        return new AppUserDetails(mockUser);
    }

    private Authentication createAuthentication(AppUserDetails userDetails) {
        return new TestingAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }

    private Claims parseToken(String token) {
        JwtParser parser = Jwts
                .parser()
                .verifyWith(testSecretKey)
                .clock(() -> Date.from(FIXED_NOW))
                .build();
        return parser.parseSignedClaims(token).getPayload();
    }

    @Test
    @DisplayName("Конструктор: JwtProperties null -> должен выбросить NullPointerException")
    void constructor_whenJwtPropertiesIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> new JwtIssuer(null, mockJwtKeyService, fixedClock))
                .withMessageContaining("jwtProperties is marked non-null but is null");
    }

    @Test
    @DisplayName("Конструктор: JwtKeyService null -> должен выбросить NullPointerException")
    void constructor_whenJwtKeyServiceIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> new JwtIssuer(mockJwtProperties, null, fixedClock))
                .withMessageContaining("jwtKeyService is marked non-null but is null");
    }

    @Test
    @DisplayName("Конструктор: Clock null -> должен выбросить NullPointerException")
    void constructor_whenClockIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> new JwtIssuer(mockJwtProperties, mockJwtKeyService, null))
                .withMessageContaining("clock is marked non-null but is null");
    }

    @Test
    @DisplayName("generateToken: Authentication Principal не AppUserDetails -> должен выбросить IllegalArgumentException")
    void generateToken_whenAuthenticationPrincipalIsNotAppUserDetails_shouldThrowIllegalArgumentException() {
        Authentication mockAuth = new TestingAuthenticationToken("notAppUserDetails", null);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtIssuer.generateToken(mockAuth))
                .withMessageContaining("Principal in Authentication object must be an instance of AppUserDetails");
    }

    @Test
    @DisplayName("generateToken: Authentication null -> должен выбросить NullPointerException")
    void generateToken_whenAuthenticationIsNull_shouldThrowNullPointerException() {
        assertThatNullPointerException()
                .isThrownBy(() -> jwtIssuer.generateToken(null)) // Передаем null Authentication
                .withMessageContaining("authentication is marked non-null but is null");
    }

    @Test
    @DisplayName("generateToken: Валидный AppUserDetails -> должен вернуть непустую JWT строку")
    void generateToken_withValidAppUserDetails_shouldReturnNonNullNonEmptyJwtString() {
        AppUserDetails userDetails = createAppUserDetails(USER_ID, USER_EMAIL);
        Authentication auth = createAuthentication(userDetails);

        String token = jwtIssuer.generateToken(auth);

        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("generateToken: Должен установить корректный Subject (userId) claim")
    void generateToken_withValidAppUserDetails_shouldSetCorrectSubjectClaimFromUserId() {
        AppUserDetails userDetails = createAppUserDetails(USER_ID, USER_EMAIL);
        Authentication auth = createAuthentication(userDetails);

        String token = jwtIssuer.generateToken(auth);
        Claims claims = parseToken(token);

        assertThat(claims.getSubject()).isEqualTo(String.valueOf(USER_ID));
    }

    @Test
    @DisplayName("generateToken: Должен установить корректный email claim (с дефолтным именем)")
    void generateToken_withValidAppUserDetails_shouldSetCorrectEmailClaimWithDefaultName() {
        AppUserDetails userDetails = createAppUserDetails(USER_ID, USER_EMAIL);
        Authentication auth = createAuthentication(userDetails);

        String token = jwtIssuer.generateToken(auth);
        Claims claims = parseToken(token);

        assertThat(claims.get(DEFAULT_EMAIL_CLAIM_KEY, String.class)).isEqualTo(USER_EMAIL);
    }

    @Test
    @DisplayName("generateToken: Должен установить корректный email claim (с кастомным именем)")
    void generateToken_withValidAppUserDetails_shouldSetCorrectEmailClaimWithCustomName() {
        AppUserDetails userDetails = createAppUserDetails(USER_ID, USER_EMAIL);
        Authentication auth = createAuthentication(userDetails);
        // Перенастраиваем мок для этого теста
        when(mockJwtProperties.getEmailClaimKey()).thenReturn(CUSTOM_EMAIL_CLAIM_KEY);

        String token = jwtIssuer.generateToken(auth);
        Claims claims = parseToken(token);

        assertThat(claims.get(CUSTOM_EMAIL_CLAIM_KEY, String.class)).isEqualTo(USER_EMAIL);
        assertThat(claims.get(DEFAULT_EMAIL_CLAIM_KEY)).isNull(); // Проверяем, что дефолтного нет
    }

    @Test
    @DisplayName("generateToken: Без authorities -> должен установить пустой authorities claim (с дефолтным именем)")
    void generateToken_withValidAppUserDetailsAndNoAuthorities_shouldSetEmptyAuthoritiesClaimWithDefaultName() {
        AppUserDetails userDetails = createAppUserDetails(USER_ID, USER_EMAIL); // authorities будут пустые
        Authentication auth = createAuthentication(userDetails);

        String token = jwtIssuer.generateToken(auth);
        Claims claims = parseToken(token);

        assertThat(claims.get(DEFAULT_AUTHORITIES_CLAIM_KEY, String.class)).isEmpty();
    }

    @Test
    @DisplayName("generateToken: Без authorities -> должен установить пустой authorities claim (с кастомным именем)")
    void generateToken_withValidAppUserDetailsAndNoAuthorities_shouldSetEmptyAuthoritiesClaimWithCustomName() {
        AppUserDetails userDetails = createAppUserDetails(USER_ID, USER_EMAIL);
        Authentication auth = createAuthentication(userDetails);
        // Перенастраиваем мок для этого теста
        when(mockJwtProperties.getAuthoritiesClaimKey()).thenReturn(CUSTOM_AUTHORITIES_CLAIM_KEY);

        String token = jwtIssuer.generateToken(auth);
        Claims claims = parseToken(token);

        assertThat(claims.get(CUSTOM_AUTHORITIES_CLAIM_KEY, String.class)).isEmpty();
        assertThat(claims.get(DEFAULT_AUTHORITIES_CLAIM_KEY)).isNull();
    }

    @Test
    @DisplayName("generateToken: Должен установить корректные iat и exp claims на основе Clock")
    void generateToken_withValidAppUserDetails_shouldSetCorrectIssuedAtAndExpirationClaimsBasedOnClock() {
        AppUserDetails userDetails = createAppUserDetails(USER_ID, USER_EMAIL);
        Authentication auth = createAuthentication(userDetails);

        String token = jwtIssuer.generateToken(auth);
        Claims claims = parseToken(token);

        Date expectedIssuedAt = Date.from(FIXED_NOW);
        Date expectedExpiration = Date.from(FIXED_NOW.plusMillis(EXPIRATION_MS));

        assertThat(claims.getIssuedAt()).isEqualTo(expectedIssuedAt);
        assertThat(claims.getExpiration()).isEqualTo(expectedExpiration);
    }

    @Test
    @DisplayName("generateToken: Должен быть подписан правильным ключом и алгоритмом HS256")
    void generateToken_shouldBeSignedWithCorrectKeyAndAlgorithm() {
        AppUserDetails userDetails = createAppUserDetails(USER_ID, USER_EMAIL);
        Authentication auth = createAuthentication(userDetails);

        String token = jwtIssuer.generateToken(auth);

        // 1. Проверка, что парсится с тем же ключом
        assertThatCode(() -> parseToken(token)).doesNotThrowAnyException();

        // 2. Проверка, что не парсится с другим ключом
        SecretKey wrongKey = Keys.hmacShaKeyFor("AnotherWrongSecretKeyWhichIsAlsoSufficientlyLong".getBytes());
        JwtParser wrongParser = Jwts.parser().verifyWith(wrongKey).build();
        assertThatThrownBy(() -> wrongParser.parseSignedClaims(token))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);

        // 3. Проверка алгоритма в заголовке
        JwsHeader header = Jwts.parser()
                .verifyWith(testSecretKey)
                .clock(() -> Date.from(FIXED_NOW)).build()
                .parseSignedClaims(token)
                .getHeader();
        assertThat(header.getAlgorithm()).isEqualTo(Jwts.SIG.HS256.getId()); // Jwts.SIG.HS256.getId() вернет "HS256"
    }
}