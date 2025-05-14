package com.example.tasktracker.backend.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для запроса на аутентификацию (логин) пользователя.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    /**
     * Email адрес пользователя для входа.
     */
    @NotBlank(message = "{user.validation.email.notBlank}")
    @Email(message = "{user.validation.email.invalidFormat}")
    private String email;

    /**
     * Пароль пользователя для входа.
     */
    @NotBlank(message = "{user.validation.password.notBlank}")
    private String password;

}