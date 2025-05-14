package com.example.tasktracker.backend.security.service;

import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.security.dto.AuthResponse;
import com.example.tasktracker.backend.security.dto.LoginRequest;
import com.example.tasktracker.backend.security.dto.RegisterRequest;
import com.example.tasktracker.backend.security.exception.PasswordMismatchException;
import com.example.tasktracker.backend.security.exception.UserAlreadyExistsException;
import com.example.tasktracker.backend.security.jwt.JwtIssuer;
import com.example.tasktracker.backend.security.jwt.JwtProperties;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.repository.UserRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис для операций регистрации и аутентификации пользователей.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtIssuer jwtIssuer;
    private final JwtProperties jwtProperties;

    /**
     * Регистрирует нового пользователя в системе.
     * В случае успеха, пользователь также автоматически аутентифицируется,
     * и для него генерируется JWT.
     *
     * @param request DTO с данными для регистрации. Не должен быть null.
     * @return {@link AuthResponse}, содержащий JWT.
     * @throws UserAlreadyExistsException если пользователь с таким email уже существует.
     * @throws PasswordMismatchException  если пароли в запросе не совпадают.
     */
    @Transactional // Операция регистрации должна быть транзакционной
    public AuthResponse register(@NonNull RegisterRequest request) {
        if (!request.getPassword().equals(request.getRepeatPassword())) {
            // Ключ сообщения для PasswordMismatchException может быть определен в GlobalExceptionHandler
            // или сообщение передается напрямую.
            throw new PasswordMismatchException("Passwords do not match.");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("User with email " + request.getEmail() + " already exists.");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // Поле User.id будет сгенерировано при сохранении.

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getEmail());

        // Авто-логин после регистрации
        AppUserDetails userDetails = new AppUserDetails(savedUser);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null, // Пароль не нужен для уже аутентифицированного principal
                userDetails.getAuthorities()
        );
        // Устанавливаем Authentication в SecurityContext (хотя для JWT это не обязательно для самого токена,
        // но может быть полезно, если сразу после регистрации идут какие-то действия, требующие SecurityContext)
        // SecurityContextHolder.getContext().setAuthentication(authentication); // Решили не делать, см. след. пункт

        // Генерируем JWT для этого Authentication
        String accessToken = jwtIssuer.generateToken(authentication);

        // TODO: Асинхронная отправка приветственного email (когда будет EmailSender и Kafka). transactional!!

        return new AuthResponse(accessToken, jwtProperties.getExpirationMs());
    }

    /**
     * Аутентифицирует пользователя по email и паролю.
     *
     * @param request DTO с данными для логина. Не должен быть null.
     * @return {@link AuthResponse}, содержащий JWT.
     * @throws org.springframework.security.core.AuthenticationException если аутентификация не удалась (например, неверные креды).
     */
    public AuthResponse login(@NonNull LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // SecurityContextHolder.getContext().setAuthentication(authentication);
        // Обычно делает сам Spring при успехе authenticate()

        log.info("User authenticated successfully: {}", request.getEmail());
        String accessToken = jwtIssuer.generateToken(authentication);
        return new AuthResponse(accessToken, jwtProperties.getExpirationMs());
    }
}