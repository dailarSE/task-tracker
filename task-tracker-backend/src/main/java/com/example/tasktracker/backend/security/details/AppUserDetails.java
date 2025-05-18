package com.example.tasktracker.backend.security.details;

import com.example.tasktracker.backend.user.entity.User;
import lombok.Getter;

import lombok.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

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

    /**
     * -- GETTER --
     *  Возвращает уникальный идентификатор пользователя.
     *  <p>
     *  Для корректно созданного экземпляра
     *  это значение гарантированно не null.
     *  </p>
     */
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
        this.id = Objects.requireNonNull(user.getId(), "User ID cannot be null when creating AppUserDetails");
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

    /**
     * Возвращает email пользователя, который используется в качестве имени пользователя (username).
     * <p>
     * Для успешно аутентифицированного пользователя это значение гарантированно не null и не пустое.
     * </p>
     * @return Email пользователя.
     */
    @Override
    public String getUsername() {
        return email;
    }

}