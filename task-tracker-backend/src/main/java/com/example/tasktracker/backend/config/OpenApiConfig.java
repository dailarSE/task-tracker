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
        servers = {
                @Server(
                        description = "Development Server"
                )
        },
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

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
                        try {
                                Components externalComponents = loadComponentsFromYaml("classpath:/openapi-components.yml");
                                if (externalComponents != null) {
                                        Components existingComponents = openApi.getComponents();
                                        if (existingComponents == null) {
                                                openApi.setComponents(externalComponents);
                                        } else {
                                                mergeComponents(existingComponents, externalComponents);
                                        }
                                        log.info("Successfully merged components from openapi-components.yml");
                                }
                        } catch (IOException e) {
                                log.error("Failed to load or parse external OpenAPI components file.", e);
                                throw new RuntimeException("Failed to initialize OpenAPI documentation from external file", e);
                        }
                };
        }

        private Components loadComponentsFromYaml(String resourcePath) throws IOException {
                final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
                Resource resource = new PathMatchingResourcePatternResolver().getResource(resourcePath);

                if (!resource.exists()) {
                        log.warn("OpenAPI components file not found at path: {}. Skipping merge.", resourcePath);
                        return null;
                }

                try (InputStream inputStream = resource.getInputStream()) {
                        OpenAPI parsedOpenApi = yamlMapper.readValue(inputStream, OpenAPI.class);
                        return parsedOpenApi != null ? parsedOpenApi.getComponents() : null;
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
                if (source.getSchemas() != null) {
                        if (target.getSchemas() == null) target.setSchemas(source.getSchemas());
                        else target.getSchemas().putAll(source.getSchemas());
                }
                if (source.getResponses() != null) {
                        if (target.getResponses() == null) target.setResponses(source.getResponses());
                        else target.getResponses().putAll(source.getResponses());
                }
                if (source.getParameters() != null) {
                        if (target.getParameters() == null) target.setParameters(source.getParameters());
                        else target.getParameters().putAll(source.getParameters());
                }
                if (source.getExamples() != null) {
                        if (target.getExamples() == null) target.setExamples(source.getExamples());
                        else target.getExamples().putAll(source.getExamples());
                }
                if (source.getRequestBodies() != null) {
                        if (target.getRequestBodies() == null) target.setRequestBodies(source.getRequestBodies());
                        else target.getRequestBodies().putAll(source.getRequestBodies());
                }
                if (source.getHeaders() != null) {
                        if (target.getHeaders() == null) target.setHeaders(source.getHeaders());
                        else target.getHeaders().putAll(source.getHeaders());
                }
                if (source.getSecuritySchemes() != null) {
                        if (target.getSecuritySchemes() == null) target.setSecuritySchemes(source.getSecuritySchemes());
                        else target.getSecuritySchemes().putAll(source.getSecuritySchemes());
                }
                if (source.getLinks() != null) {
                        if (target.getLinks() == null) target.setLinks(source.getLinks());
                        else target.getLinks().putAll(source.getLinks());
                }
                if (source.getCallbacks() != null) {
                        if (target.getCallbacks() == null) target.setCallbacks(source.getCallbacks());
                        else target.getCallbacks().putAll(source.getCallbacks());
                }
                if (source.getExtensions() != null) {
                        if (target.getExtensions() == null) target.setExtensions(source.getExtensions());
                        else target.getExtensions().putAll(source.getExtensions());
                }
                if (source.getPathItems() != null) {
                        if (target.getPathItems() == null) target.setPathItems(source.getPathItems());
                        else target.getPathItems().putAll(source.getPathItems());
                }
        }
}