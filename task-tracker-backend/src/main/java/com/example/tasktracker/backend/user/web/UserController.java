package com.example.tasktracker.backend.user.web;

import com.example.tasktracker.backend.security.common.ControllerSecurityUtils;
import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.security.dto.AuthResponse;
import com.example.tasktracker.backend.security.dto.RegisterRequest;
import com.example.tasktracker.backend.security.service.AuthService;
import com.example.tasktracker.backend.user.dto.UserResponse;
import com.example.tasktracker.backend.web.exception.GlobalExceptionHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

import static com.example.tasktracker.backend.web.ApiConstants.USERS_API_BASE_URL;
import static com.example.tasktracker.backend.web.ApiConstants.X_ACCESS_TOKEN_HEADER;

/**
 * REST-контроллер для операций, связанных с пользователями.
 * <p>
 * Предоставляет эндпоинты для управления пользовательскими аккаунтами,
 * включая регистрацию новых пользователей и получение информации о текущем
 * аутентифицированном пользователе.
 * </p>
 * <p>
 * Все ошибки, возникающие при обработке запросов (например, ошибки валидации DTO
 * или бизнес-исключения из {@link AuthService}), обрабатываются глобально
 * в {@link GlobalExceptionHandler} и возвращаются клиенту в формате
 * RFC 9457 Problem Details.
 * </p>
 *
 * @see AuthService Сервис, инкапсулирующий логику регистрации и аутентификации.
 * @see GlobalExceptionHandler Глобальный обработчик исключений.
 * @see RegisterRequest DTO для запроса на регистрацию.
 * @see AuthResponse DTO для ответа, содержащего JWT.
 * @see UserResponse DTO для ответа с информацией о пользователе.
 * @see ControllerSecurityUtils Утилиты для работы с principal в контроллерах.
 * @see AppUserDetails Детали аутентифицированного пользователя.
 */
@RestController
@RequestMapping(USERS_API_BASE_URL)
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users", description = "API для управления пользователями и их профилями")
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
     * либо ответ об ошибке, сформированный {@link GlobalExceptionHandler}.
     */
    @Operation(summary = "Регистрация нового пользователя")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",
                    description = "Пользователь успешно создан и аутентифицирован",
                    headers = {
                            @Header(name = X_ACCESS_TOKEN_HEADER, description = "JWT Access Token"),
                            @Header(name = HttpHeaders.LOCATION, description = "URI для получения информации о текущем пользователе")
                    },
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequestRegistration"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ConflictUserExists")
    })
    @SecurityRequirements() // Публичный эндпоинт
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
        return ResponseEntity.status(HttpStatus.CREATED).headers(responseHeaders).body(authResponse);
    }

    /**
     * Получает информацию о текущем аутентифицированном пользователе.
     * <p>
     * Эндпоинт: {@code GET /api/v1/users/me}
     * </p>
     * <p>
     * Доступен только для аутентифицированных пользователей. При успешном запросе
     * возвращает HTTP статус 200 OK с {@link UserResponse} в теле, содержащим
     * ID и email текущего пользователя.
     * </p>
     * <p>
     * В случае отсутствия или невалидности JWT, запрос будет отклонен на уровне
     * фильтров безопасности с HTTP статусом 401 Unauthorized.
     * </p>
     *
     * @param currentUserPrincipal Данные текущего аутентифицированного пользователя (реализация {@link AppUserDetails}),
     *                             внедренные Spring Security. Ожидается, что этот параметр будет не-null
     *                             для защищенного эндпоинта.
     * @return {@link ResponseEntity} с {@link UserResponse} в теле, содержащим ID и email пользователя, и статусом 200 OK.
     * @throws IllegalStateException если {@code currentUserPrincipal} не может быть разрешен в корректный {@link AppUserDetails} с ID (выбрасывается из {@link ControllerSecurityUtils}).
     */
    @Operation(summary = "Получение данных текущего пользователя")
    @ApiResponse(responseCode = "200", description = "Данные пользователя успешно получены",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "401", ref = "#/components/responses/UnauthorizedGeneral")
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal AppUserDetails currentUserPrincipal) {
        AppUserDetails userDetails = ControllerSecurityUtils.getAuthenticatedUserDetails(currentUserPrincipal);

        log.info("Processing request to get current user details for userId: {}", userDetails.getId());
        UserResponse response = new UserResponse(userDetails.getId(), userDetails.getUsername());
        log.info("Successfully retrieved current user details for userId: {}", userDetails.getId());
        return ResponseEntity.ok(response);
    }
}