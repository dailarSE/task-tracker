package com.example.tasktracker.backend.web;

/**
 * Класс-контейнер для общих констант, используемых в веб-слое API.
 * <p>
 * Этот класс не предназначен для инстанцирования и содержит только статические
 * константы для URL-префиксов, имен заголовков и т.д.
 * </p>
 */
public final class ApiConstants {

    private ApiConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Базовый префикс для всех API эндпоинтов первой версии.
     * Значение: {@value}.
     */
    public static final String API_V1_PREFIX = "/api/v1";

    /**
     * Базовый путь для ресурсов, связанных с пользователями.
     * Конкатенируется с {@link #API_V1_PREFIX}.
     * Значение: {@value}.
     */
    public static final String USERS_BASE_PATH = "/users";

    /**
     * Базовый путь для эндпоинтов аутентификации.
     * Конкатенируется с {@link #API_V1_PREFIX}.
     * Значение: {@value}.
     */
    public static final String AUTH_BASE_PATH = "/auth";

    /**
     * Имя HTTP-заголовка для передачи JWT Access Token в ответе сервера.
     * Значение: {@value}.
     */
    public static final String X_ACCESS_TOKEN_HEADER = "X-Access-Token";

    // --- Полные пути для удобства использования в контроллерах ---

    /**
     * Полный базовый URL для эндпоинтов пользователей.
     * Значение: {@value API_V1_PREFIX}{@value USERS_BASE_PATH}.
     */
    public static final String USERS_API_BASE_URL = API_V1_PREFIX + USERS_BASE_PATH;

    /**
     * Полный базовый URL для эндпоинтов аутентификации.
     * Значение: {@value API_V1_PREFIX}{@value AUTH_BASE_PATH}.
     */
    public static final String AUTH_API_BASE_URL = API_V1_PREFIX + AUTH_BASE_PATH;

    // TODO: (URI для ProblemDetail) - Определить и задокументировать пространство имен для type URI.
    public static final String PROBLEM_TYPE_BASE_URI = "https://task-tracker.example.com/probs/";

}