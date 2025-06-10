package com.example.tasktracker.backend.security.web.controller;

import com.example.tasktracker.backend.security.dto.AuthResponse;
import com.example.tasktracker.backend.security.dto.LoginRequest;
import com.example.tasktracker.backend.security.service.AuthService;
import com.example.tasktracker.backend.web.exception.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static com.example.tasktracker.backend.web.ApiConstants.LOGIN_ENDPOINT;
import static com.example.tasktracker.backend.web.ApiConstants.X_ACCESS_TOKEN_HEADER;

/**
 * REST-контроллер для операций аутентификации пользователей.
 * <p>
 * Предоставляет эндпоинт для входа пользователей в систему по их кредам (email и пароль).
 * Все ошибки, возникающие при обработке запросов (например, ошибки валидации DTO
 * или бизнес-исключения из {@link AuthService}, такие как неверные креды),
 * обрабатываются глобально в {@link GlobalExceptionHandler} и возвращаются клиенту
 * в формате RFC 9457 Problem Details.
 * </p>
 *
 * @see AuthService Сервис, инкапсулирующий логику аутентификации.
 * @see GlobalExceptionHandler Глобальный обработчик исключений.
 * @see LoginRequest DTO для запроса на аутентификацию.
 * @see AuthResponse DTO для ответа, содержащего JWT.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "API для аутентификации пользователей")
public class AuthController {

    private final AuthService authService;

    /**
     * Аутентифицирует пользователя в системе по его email и паролю.
     * <p>
     * Принимает {@link LoginRequest} в теле запроса, который должен пройти валидацию.
     * В случае успешной аутентификации:
     * <ul>
     *     <li>Возвращается HTTP статус 200 OK.</li>
     *     <li>В теле ответа содержится {@link AuthResponse} с JWT Access Token и информацией о нем.</li>
     *     <li>В заголовке ответа {@code X-Access-Token} также содержится JWT Access Token.</li>
     * </ul>
     * В случае ошибки (например, невалидные данные, неверные креды),
     * будет возвращен соответствующий HTTP статус ошибки (400, 401) с телом
     * в формате Problem Details.
     * </p>
     *
     * @param loginRequest DTO с данными для аутентификации. Должен быть аннотирован {@code @Valid}
     *                     для активации валидации.
     * @return {@link ResponseEntity} с {@link AuthResponse} в теле и статусом 200 OK,
     *         либо ответ об ошибке, сформированный {@link GlobalExceptionHandler}.
     */
    @Operation(
            summary = "Аутентификация пользователя (логин)",
            description = "Принимает email и пароль, возвращает JWT Access Token в случае успеха."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Аутентификация прошла успешно",
                    headers = @Header(name = X_ACCESS_TOKEN_HEADER, description = "JWT Access Token"),
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequestValidation"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/UnauthorizedBadCredentials")
    })

    @PostMapping(LOGIN_ENDPOINT)
    public ResponseEntity<AuthResponse> loginUser(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Processing login request for email: {}", loginRequest.getEmail());

        AuthResponse authResponse = authService.login(loginRequest);

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(X_ACCESS_TOKEN_HEADER, authResponse.getAccessToken());

        log.info("User authentication successful for email: {}. JWT issued.", loginRequest.getEmail());
        return ResponseEntity.ok().headers(responseHeaders).body(authResponse);
    }
}