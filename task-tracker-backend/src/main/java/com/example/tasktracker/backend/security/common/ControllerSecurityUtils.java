package com.example.tasktracker.backend.security.common;

import com.example.tasktracker.backend.security.details.AppUserDetails;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Утилитарный класс для общих задач безопасности в контроллерах.
 */
@UtilityClass
@Slf4j
public class ControllerSecurityUtils {

    /**
     * Извлекает {@link AppUserDetails} из текущего {@link Authentication} объекта
     * в {@link SecurityContextHolder} или непосредственно из предоставленного principal.
     * <p>
     * Этот метод предназначен для использования в контроллерах, аннотированных
     * {@code @AuthenticationPrincipal AppUserDetails currentUserDetails},
     * или для случаев, когда нужно получить AppUserDetails из SecurityContext.
     * </p>
     *
     * @param principal Объект principal, обычно внедренный через {@code @AuthenticationPrincipal}.
     *                  Может быть {@code null}, если метод вызывается не из контекста
     *                  контроллера с такой аннотацией.
     * @return Экземпляр {@link AppUserDetails}.
     * @throws IllegalStateException если principal не является {@link AppUserDetails}
     *                               или у него отсутствует ID, или если аутентификация отсутствует/некорректна.
     */
    public static AppUserDetails getAuthenticatedUserDetails(Object principal) {
        AppUserDetails userDetails = (principal instanceof AppUserDetails) ? (AppUserDetails) principal : null;

        if (userDetails == null) {
            log.warn("Authenticated principal is not available or not of type AppUserDetails. " +
                            "Provided principal type: {}",
                    (principal != null ? principal.getClass().getName() : "null"));
            throw new IllegalStateException(
                    "Authenticated principal (AppUserDetails) is not available.");
        }
        if (userDetails.getId() == null) {
            log.error("CRITICAL: AppUserDetails principal found, but its ID is null.");
            throw new IllegalStateException(
                    "Authenticated principal (AppUserDetails) has a null ID.");
        }
        return userDetails;
    }

    /**
     * Извлекает ID текущего аутентифицированного пользователя.
     * Обертка над {@link #getAuthenticatedUserDetails(Object)}.
     *
     * @param principal Объект principal.
     * @return ID пользователя.
     * @throws IllegalStateException если аутентификация некорректна или principal не содержит ID.
     */
    public static Long getCurrentUserId(Object principal) {
        return getAuthenticatedUserDetails(principal).getId();
    }
}