package com.example.tasktracker.emailsender.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "app")
@Validated
@Getter
@Setter
public class AppProperties {

    /**
     * Уникальный идентификатор инстанса приложения.
     * Используется для идемпотентности и распределенных блокировок.
     */
    @NotBlank
    private String instanceId;
}
