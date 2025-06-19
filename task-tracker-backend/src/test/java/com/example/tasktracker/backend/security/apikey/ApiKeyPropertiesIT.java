package com.example.tasktracker.backend.security.apikey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты для {@link ApiKeyProperties}, проверяющие
 * корректность биндинга и валидации свойств из конфигурации.
 */
@ActiveProfiles("ci")
class ApiKeyPropertiesIT {

    @Configuration
    @EnableConfigurationProperties(ApiKeyProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("Конфигурация: валидные ключи -> контекст загружается, свойства корректны")
    void properties_whenKeysAreValid_shouldLoadContextAndBindProperties() {
        this.contextRunner
                .withPropertyValues(
                        "app.security.api-key.valid-keys[0]=key-for-scheduler",
                        "app.security.api-key.valid-keys[1]=key-for-another-service"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ApiKeyProperties.class);

                    ApiKeyProperties props = context.getBean(ApiKeyProperties.class);
                    assertThat(props.getValidKeys())
                            .isNotNull()
                            .hasSize(2)
                            .contains("key-for-scheduler", "key-for-another-service");
                });
    }

    @Test
    @DisplayName("Конфигурация: valid-keys отсутствует -> валидация должна упасть")
    void properties_whenKeysAreMissing_shouldFailToLoadContext() {
        this.contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Конфигурация: valid-keys пустой -> валидация (@NotEmpty) должна упасть")
    void properties_whenKeysAreEmpty_shouldFailToLoadContext() {
        this.contextRunner
                .withPropertyValues("app.security.api-key.valid-keys=")
                .run(context -> assertThat(context).hasFailed());
    }
}