package com.example.tasktracker.backend.security.details;

import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис для загрузки данных пользователя для Spring Security.
 * Реализует {@link UserDetailsService}.
 */
@Service
@RequiredArgsConstructor
public class UserLoadingService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Загружает данные пользователя по его email (который используется как username).
     * Этот метод вызывается Spring Security во время процесса аутентификации (например, при логине).
     *
     * @param email Email пользователя (используется как username).
     * @return {@link UserDetails} объект, содержащий данные пользователя.
     * @throws UsernameNotFoundException если пользователь с таким email не найден.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found with email: " + email)
                );
        return new AppUserDetails(user);
    }
}