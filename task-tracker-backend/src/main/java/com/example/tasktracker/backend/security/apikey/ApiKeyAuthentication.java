package com.example.tasktracker.backend.security.apikey;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.Collection;

/**
 * Реализация {@link org.springframework.security.core.Authentication} для представления
 * аутентифицированного внутреннего сервиса по API-ключу.
 * <p>
 * Устанавливается в SecurityContext после успешной валидации API-ключа.
 * </p>
 */
@Getter
public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    /**
     * Principal для внутреннего сервиса. Может быть строкой, идентифицирующей сервис.
     */
    private final String principal;

    /**
     * Создает аутентифицированный токен.
     *
     * @param principal   Идентификатор аутентифицированного сервиса.
     * @param authorities Полномочия (обычно пустые для M2M аутентификации по ключу).
     */
    public ApiKeyAuthentication(String principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    /**
     * Создает аутентифицированный токен с пустыми полномочиями.
     *
     * @param principal Идентификатор аутентифицированного сервиса.
     * @return Экземпляр ApiKeyAuthentication.
     */
    public static ApiKeyAuthentication authenticated(String principal) {
        return new ApiKeyAuthentication(principal, AuthorityUtils.NO_AUTHORITIES);
    }

    /**
     * Учетные данные (сам ключ) не хранятся в объекте Authentication
     */
    @Override
    public Object getCredentials() {
        return null;
    }
}