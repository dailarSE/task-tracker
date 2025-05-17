package com.example.tasktracker.backend.user.web;

import com.example.tasktracker.backend.security.dto.AuthResponse;
import com.example.tasktracker.backend.security.dto.RegisterRequest;
import com.example.tasktracker.backend.security.service.AuthService;
import com.example.tasktracker.backend.web.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

import static com.example.tasktracker.backend.web.ApiConstants.*;

/**
 * REST-контроллер для операций, связанных с пользователями.
 * <p>
 * Предоставляет эндпоинты для управления пользовательскими аккаунтами.
 * На данный момент реализован только эндпоинт для регистрации новых пользователей.
 * Все ошибки, возникающие при обработке запросов (например, ошибки валидации DTO
 * или бизнес-исключения из {@link AuthService}), обрабатываются глобально
 * в {@link GlobalExceptionHandler} и возвращаются клиенту в формате
 * RFC 9457 Problem Details.
 * </p>
 *
 * @see AuthService Сервис, инкапсулирующий логику регистрации.
 * @see GlobalExceptionHandler Глобальный обработчик исключений.
 * @see RegisterRequest DTO для запроса на регистрацию.
 * @see AuthResponse DTO для ответа, содержащего JWT.
 */
@RestController
@RequestMapping(USERS_API_BASE_URL)
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final AuthService authService;

    /**
     * Регистрирует нового пользователя в системе.
     * <p>
     * Принимает {@link RegisterRequest} в теле запроса, который должен пройти валидацию.
     * В случае успешной регистрации:
     * <ul>
     *     <li>Возвращается HTTP статус 201 Created.</li>
     *     <li>В теле ответа содержится {@link AuthResponse} с JWT Access Token и информацией о нем.</li>
     *     <li>В заголовке ответа {@code X-Access-Token} также содержится JWT Access Token.</li>
     *     <li>В заголовке ответа {@code Location} содержится URI, указывающий на ресурс
     *         (гипотетически) для получения информации о текущем пользователе (например, "/api/v1/users/me").</li>
     * </ul>
     * В случае ошибки (например, невалидные данные, email уже занят, пароли не совпадают),
     * будет возвращен соответствующий HTTP статус ошибки (400, 409) с телом
     * в формате Problem Details.
     * </p>
     *
     * @param registerRequest DTO с данными для регистрации. Должен быть аннотирован {@code @Valid}
     *                        для активации валидации.
     * @return {@link ResponseEntity} с {@link AuthResponse} в теле и статусом 201 Created,
     *         либо ответ об ошибке, сформированный {@link GlobalExceptionHandler}.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        log.info("Processing user registration request for email: {}", registerRequest.getEmail());

        AuthResponse authResponse = authService.register(registerRequest);

        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path(USERS_API_BASE_URL + "/me")
                .build()
                .toUri();

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setLocation(location);
        responseHeaders.set(X_ACCESS_TOKEN_HEADER, authResponse.getAccessToken());

        log.info("User registration successful for email: {}. JWT issued.", registerRequest.getEmail());
        return new ResponseEntity<>(authResponse, responseHeaders, HttpStatus.CREATED);
    }
}