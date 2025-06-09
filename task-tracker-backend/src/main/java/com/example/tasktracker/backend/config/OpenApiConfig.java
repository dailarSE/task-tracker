package com.example.tasktracker.backend.config;


import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
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
// Определяем саму схему безопасности 'bearerAuth'
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {}