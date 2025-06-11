package com.example.tasktracker.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
@OpenAPIDefinition(
        info = @Info(
                title = "Task Tracker API",
                version = "1.0.0",
                description = "RESTful API для сервиса Task Tracker"
        ),
        servers = {@Server(description = "Development Server")},
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

    private static final String EXTERNAL_COMPONENTS_PATH = "classpath:/openapi-components.yml";

    /**
     * Создает кастомайзер, который загружает компоненты OpenAPI
     * из внешнего YAML-файла и объединяет их с автоматически сгенерированной
     * спецификацией.
     *
     * @return бин OpenApiCustomizer.
     */
    @Bean
    public OpenApiCustomizer externalComponentsCustomizer() {
        return openApi -> {
            Components externalComponents = loadComponentsFromYaml();
            Components existingComponents = openApi.getComponents();
            if (existingComponents == null) {
                openApi.setComponents(externalComponents);
            } else {
                mergeComponents(existingComponents, externalComponents);
            }

            log.info("Successfully merged components from " + EXTERNAL_COMPONENTS_PATH);
        };
    }

    private Components loadComponentsFromYaml() {
        final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        Resource resource = new PathMatchingResourcePatternResolver().getResource(EXTERNAL_COMPONENTS_PATH);

        if (!resource.exists()) {
            log.error("CRITICAL: OpenAPI components file not found at path: {}. Application startup will be aborted.",
                    EXTERNAL_COMPONENTS_PATH);
            throw new IllegalStateException("Required OpenAPI components file not found: " + EXTERNAL_COMPONENTS_PATH);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            OpenAPI parsedOpenApi = yamlMapper.readValue(inputStream, OpenAPI.class);
            if (parsedOpenApi == null || parsedOpenApi.getComponents() == null) {
                log.error("CRITICAL: OpenAPI components file is empty or does not contain a 'components' section: {}. " +
                        "Startup aborted.", EXTERNAL_COMPONENTS_PATH);
                throw new IllegalStateException("OpenAPI components file is empty or invalid: " + EXTERNAL_COMPONENTS_PATH);
            }
            return parsedOpenApi.getComponents();
        } catch (IOException e) {
            log.error("CRITICAL: Failed to load or parse external OpenAPI components file: {}. Startup aborted.",
                    EXTERNAL_COMPONENTS_PATH, e);
            throw new IllegalStateException("Failed to read or parse OpenAPI components file: " +
                    EXTERNAL_COMPONENTS_PATH, e);
        }
    }

    /**
     * Вспомогательный метод для полного слияния компонентов.
     * Добавляет все типы компонентов из `source` в `target`.
     * Если компонент с таким же именем уже существует, он будет заменен.
     *
     * @param target Целевые компоненты (из основной спецификации).
     * @param source Исходные компоненты (из файла).
     */
    private void mergeComponents(Components target, Components source) {
        if (source.getSchemas() != null) source.getSchemas().forEach(target::addSchemas);
        if (source.getResponses() != null) source.getResponses().forEach(target::addResponses);
        if (source.getParameters() != null) source.getParameters().forEach(target::addParameters);
        if (source.getExamples() != null) source.getExamples().forEach(target::addExamples);
        if (source.getRequestBodies() != null) source.getRequestBodies().forEach(target::addRequestBodies);
        if (source.getHeaders() != null) source.getHeaders().forEach(target::addHeaders);
        if (source.getSecuritySchemes() != null) source.getSecuritySchemes().forEach(target::addSecuritySchemes);
        if (source.getLinks() != null) source.getLinks().forEach(target::addLinks);
        if (source.getCallbacks() != null) source.getCallbacks().forEach(target::addCallbacks);
        if (source.getExtensions() != null) source.getExtensions().forEach(target::addExtension);
        if (source.getPathItems() != null) source.getPathItems().forEach(target::addPathItem);
    }
}