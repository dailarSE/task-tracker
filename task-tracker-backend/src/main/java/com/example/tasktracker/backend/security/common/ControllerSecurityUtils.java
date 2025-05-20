package com.example.tasktracker.backend.security.common;

import com.example.tasktracker.backend.security.details.AppUserDetails;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.InsufficientAuthenticationException;
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
     * @throws InsufficientAuthenticationException если principal не является {@link AppUserDetails}
     *                                             или если аутентификация отсутствует/некорректна.
     */
    public static AppUserDetails getAuthenticatedUserDetails(Object principal) {
        AppUserDetails userDetails = getAppUserDetails(principal);

        if (userDetails == null) {
            log.warn("Authenticated principal is not available or not of type AppUserDetails. " +
                            "Provided principal type: {}",
                    (principal != null ? principal.getClass().getName() : "null"));
            throw new InsufficientAuthenticationException(
                    "Authenticated principal (AppUserDetails) is not available.");
        }
        if (userDetails.getId() == null) {
            log.error("CRITICAL: AppUserDetails principal found, but its ID is null.");
            throw new InsufficientAuthenticationException(
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
     * @throws InsufficientAuthenticationException если аутентификация некорректна.
     */
    public static Long getCurrentUserId(Object principal) {
        return getAuthenticatedUserDetails(principal).getId();
    }


    private static AppUserDetails getAppUserDetails(Object principal) {
        AppUserDetails userDetails = null;

        if (principal instanceof AppUserDetails) {
            userDetails = (AppUserDetails) principal;
        } else {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() &&
                    authentication.getPrincipal() instanceof AppUserDetails) {
                userDetails = (AppUserDetails) authentication.getPrincipal();
            }
        }
        return userDetails;
    }
}