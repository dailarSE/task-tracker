package com.example.tasktracker.backend.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DTO для запроса на регистрацию нового пользователя.
 * Содержит данные, необходимые для создания аккаунта.
 */
@Getter
@AllArgsConstructor
public class RegisterRequest {

    /**
     * Email адрес пользователя. Должен быть уникальным и в корректном формате.
     * Используется как логин пользователя.
     */
    @NotBlank(message = "{user.validation.email.notBlank}")
    @Email(message = "{user.validation.email.invalidFormat}")
    @Size(max = 255, message = "{user.validation.email.size}")
    private final String email;

    /**
     * Пароль пользователя. Должен соответствовать требованиям к минимальной длине.
     */
    @NotBlank(message = "{user.validation.password.notBlank}")
    @Size(max = 255, message = "{user.validation.password.size}")
    private final String password;

    /**
     * Подтверждение пароля пользователя. Должно совпадать с полем password.
     */
    @NotBlank(message = "{user.validation.repeatPassword.notBlank}")
    private final String repeatPassword;

}