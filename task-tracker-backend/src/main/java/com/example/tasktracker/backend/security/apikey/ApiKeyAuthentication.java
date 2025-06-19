package com.example.tasktracker.backend.security.apikey;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * Реализация {@link org.springframework.security.core.Authentication} для представления
 * аутентифицированного внутреннего сервиса по API-ключу.
 * <p>
 * Устанавливается в SecurityContext после успешной валидации API-ключа.
 * </p>
 */
@Getter
public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final String serviceId;
    private final String instanceId;

    /**
     * Конструктор для создания аутентифицированного токена.
     *
     * @param serviceId  Идентификатор типа сервиса (например, "task-tracker-scheduler").
     * @param instanceId Уникальный идентификатор экземпляра сервиса.
     */
    public ApiKeyAuthentication(String serviceId, String instanceId) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.serviceId = serviceId;
        this.instanceId = instanceId;
        setAuthenticated(true);
    }

    /**
     * Учетные данные (сам ключ) не хранятся в объекте Authentication
     */
    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return serviceId;
    }
}