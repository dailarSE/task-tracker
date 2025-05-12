package com.example.tasktracker.backend.security.details;

import com.example.tasktracker.backend.user.entity.User;
import lombok.Getter;

import lombok.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Содержит основные данные пользователя, необходимые Spring Security.
 */

public class AppUserDetails implements UserDetails {

    @Getter
    private final Long id;
    private final String email;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities; // Пока пустая коллекция

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