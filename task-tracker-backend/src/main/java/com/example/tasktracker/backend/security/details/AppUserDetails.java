package com.example.tasktracker.backend.security.details;

import com.example.tasktracker.backend.user.entity.User;
import lombok.Getter;

import lombok.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Реализация интерфейса {@link org.springframework.security.core.userdetails.UserDetails},
 * инкапсулирующая основные данные пользователя (ID, email, хешированный пароль, права доступа),
 * необходимые для Spring Security в процессе аутентификации и авторизации.
 * Email используется в качестве имени пользователя (username).
 *
 * @see com.example.tasktracker.backend.user.entity.User
 * @see org.springframework.security.core.userdetails.UserDetails
 */

public class AppUserDetails implements UserDetails {

    @Getter
    private final Long id;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * Конструктор для создания AppUserDetails на основе сущности User.
     *
     * @param user Сущность пользователя из базы данных. Не должна быть null.
     * @throws NullPointerException если user равен null.
     */
    public AppUserDetails(@NonNull User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.password = user.getPassword();

        this.authorities = Collections.emptyList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }
}