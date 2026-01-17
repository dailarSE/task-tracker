package com.example.tasktracker.emailsender.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "app.email")
@Validated
@Getter
@Setter
public class EmailSenderProperties {

    /**
     * Адрес, от имени которого будут отправляться письма (поле From).
     */
    @NotBlank
    @Email
    private String senderAddress;
}