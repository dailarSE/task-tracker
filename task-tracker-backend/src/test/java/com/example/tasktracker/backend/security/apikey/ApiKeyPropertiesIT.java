package com.example.tasktracker.backend.security.apikey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("ci")
class ApiKeyPropertiesIT {

    @Configuration
    @EnableConfigurationProperties(ApiKeyProperties.class)
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("Конфигурация: валидные ключи -> контекст загружается, карта корректна")
    void properties_whenKeysAreValid_shouldLoadContextAndBindMap() {
        this.contextRunner
                .withPropertyValues(
                        "app.security.api-key.keys-to-services.my-secret-key-1=scheduler-service",
                        "app.security.api-key.keys-to-services.my-secret-key-2=testing-client"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ApiKeyProperties props = context.getBean(ApiKeyProperties.class);
                    assertThat(props.getKeysToServices())
                            .isNotNull()
                            .hasSize(2)
                            .containsEntry("my-secret-key-1", "scheduler-service")
                            .containsEntry("my-secret-key-2", "testing-client");
                });
    }

    @Test
    @DisplayName("Конфигурация: keys-to-services отсутствует -> валидация должна упасть")
    void properties_whenMapIsMissing_shouldFailToLoadContext() {
        this.contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("Конфигурация: keys-to-services пустой -> валидация (@NotEmpty) должна упасть")
    void properties_whenMapIsEmpty_shouldFailToLoadContext() {
        this.contextRunner
                .withPropertyValues("app.security.api-key.keys-to-services=")
                .run(context -> assertThat(context).hasFailed());
    }
}