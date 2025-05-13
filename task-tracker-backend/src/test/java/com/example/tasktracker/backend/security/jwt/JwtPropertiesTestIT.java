package com.example.tasktracker.backend.security.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("ci")
class JwtPropertiesValidationIT {

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("Конфигурация: все свойства валидны -> контекст загружается, свойства корректны")
    void properties_whenAllValid_shouldLoadContextAndBindProperties() {
        this.contextRunner
                .withPropertyValues(
                        "app.security.jwt.secret-key=c2VjcmV0S2V5Rm9yVGVzdGluZ1B1cnBvc2VzTXVzdEJlTG9uZ0Vub3VnaA==",
                        "app.security.jwt.expiration-ms=60000"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed(); // Контекст должен успешно загрузиться
                    assertThat(context).hasSingleBean(JwtProperties.class); // Убедимся, что бин создан

                    JwtProperties props = context.getBean(JwtProperties.class);
                    assertThat(props.getSecretKey()).isEqualTo("c2VjcmV0S2V5Rm9yVGVzdGluZ1B1cnBvc2VzTXVzdEJlTG9uZ0Vub3VnaA==");
                    assertThat(props.getExpirationMs()).isEqualTo(60000L);
                    assertThat(props.getAuthoritiesClaimKey()).isEqualTo("authorities");
                    assertThat(props.getEmailClaimKey()).isEqualTo("email");
                });
    }

    @Test
    @DisplayName("Конфигурация: все свойства валидны (с переопределенными claim keys) -> контекст загружается, свойства корректны")
    void properties_whenAllValidWithOverriddenClaimKeys_shouldLoadContextAndBindProperties() {
        this.contextRunner
                .withPropertyValues(
                        "app.security.jwt.secret-key=c2VjcmV0S2V5Rm9yVGVzdGluZ1B1cnBvc2VzTXVzdEJlTG9uZ0Vub3VnaA==",
                        "app.security.jwt.expiration-ms=60000",
                        "app.security.jwt.email-claim-key=user_email_claim",
                        "app.security.jwt.authorities-claim-key=user_auths_claim"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtProperties.class);

                    JwtProperties props = context.getBean(JwtProperties.class);
                    assertThat(props.getSecretKey()).isEqualTo("c2VjcmV0S2V5Rm9yVGVzdGluZ1B1cnBvc2VzTXVzdEJlTG9uZ0Vub3VnaA==");
                    assertThat(props.getExpirationMs()).isEqualTo(60000L);
                    assertThat(props.getEmailClaimKey()).isEqualTo("user_email_claim"); // Проверка переопределенного значения
                    assertThat(props.getAuthoritiesClaimKey()).isEqualTo("user_auths_claim"); // Проверка переопределенного значения
                });
    }

    @Test
    @DisplayName("Конфигурация: secret-key отсутствует -> валидация должна упасть при старте (контекст не загружается)")
    void properties_whenSecretKeyIsMissing_shouldFailToLoadContext() {
        this.contextRunner
                .withPropertyValues(
                        // secret-key не указан
                        "app.security.jwt.expiration-ms=60000"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @DisplayName("Конфигурация: secret-key пустой или состоит из пробелов -> валидация должна упасть при старте")
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void properties_whenSecretKeyIsBlank_shouldFailToLoadContext(String blankSecretKey) {
        this.contextRunner
                .withPropertyValues(
                        "app.security.jwt.secret-key=" + blankSecretKey,
                        "app.security.jwt.expiration-ms=60000"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Конфигурация: expiration-ms отсутствует -> валидация должна упасть при старте")
    void properties_whenExpirationMsIsMissing_shouldFailToLoadContext() {
        this.contextRunner
                .withPropertyValues(
                        "app.security.jwt.secret-key=c2VjcmV0S2V5Rm9yVGVzdGluZ1B1cnBvc2VzTXVzdEJlTG9uZ0Vub3VnaA=="
                        // expiration-ms не указан
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @DisplayName("Конфигурация: expiration-ms ноль или отрицательное -> валидация (@Positive) должна упасть при старте")
    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, -10000L})
    void properties_whenExpirationMsIsZeroOrNegative_shouldFailToLoadContext(long invalidExpirationMs) {
        this.contextRunner
                .withPropertyValues(
                        "app.security.jwt.secret-key=c2VjcmV0S2V5Rm9yVGVzdGluZ1B1cnBvc2VzTXVzdEJlTG9uZ0Vub3VnaA==",
                        "app.security.jwt.expiration-ms=" + invalidExpirationMs
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @DisplayName("Конфигурация: email-claim-key пустой или состоит из пробелов -> валидация должна упасть")
    @ParameterizedTest(name = "email-claim-key=\"{0}\"")
    @ValueSource(strings = {"", " ", "   "})
    void properties_whenEmailClaimKeyIsBlank_shouldFailToLoadContext(String blankEmailClaimKey) {
        this.contextRunner
                .withPropertyValues(
                        "app.security.jwt.secret-key=c2VjcmV0S2V5Rm9yVGVzdGluZ1B1cnBvc2VzTXVzdEJlTG9uZ0Vub3VnaA==",
                        "app.security.jwt.expiration-ms=60000",
                        "app.security.jwt.email-claim-key=" + blankEmailClaimKey
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @DisplayName("Конфигурация: authorities-claim-key пустой или состоит из пробелов -> валидация должна упасть")
    @ParameterizedTest(name = "authorities-claim-key=\"{0}\"")
    @ValueSource(strings = {"", " ", "   "})
    void properties_whenAuthoritiesClaimKeyIsBlank_shouldFailToLoadContext(String blankAuthoritiesClaimKey) {
        this.contextRunner
                .withPropertyValues(
                        "app.security.jwt.secret-key=c2VjcmV0S2V5Rm9yVGVzdGluZ1B1cnBvc2VzTXVzdEJlTG9uZ0Vub3VnaA==",
                        "app.security.jwt.expiration-ms=60000",
                        "app.security.jwt.authorities-claim-key=" + blankAuthoritiesClaimKey
                )
                .run(context -> assertThat(context).hasFailed());
    }
}