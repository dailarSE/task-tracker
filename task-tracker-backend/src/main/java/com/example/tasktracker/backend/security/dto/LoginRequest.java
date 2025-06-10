package com.example.tasktracker.backend.security.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для запроса на аутентификацию (логин) пользователя.
 */
@Schema(description = "DTO для запроса на аутентификацию (логин)")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    /**
     * Email адрес пользователя для входа.
     */
    @Schema(description = "Email адрес пользователя.", example = "user@example.com")
    @NotBlank(message = "{user.validation.email.notBlank}")
    @Email(message = "{user.validation.email.invalidFormat}")
    @Size(max = 255, message = "{user.validation.email.size}")
    private String email;

    /**
     * Пароль пользователя для входа.
     */
    @Schema(description = "Пароль пользователя.", example = "MyP@ssw0rd123")
    @NotBlank(message = "{user.validation.password.notBlank}")
    @Size(max = 255, message = "{user.validation.password.size}")
    private String password;

}