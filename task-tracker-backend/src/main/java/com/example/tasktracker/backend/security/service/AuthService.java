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
import org.springframework.dao.DataIntegrityViolationException;
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
     * <p>
     * В случае успеха, пользователь также автоматически аутентифицируется,
     * и для него генерируется JWT. После успешного сохранения, идентификатор
     * нового пользователя (userId) логируется для связи с {@code traceId} текущего запроса.
     * Для данного запроса регистрации {@code userId} не помещается в MDC (Mapped Diagnostic Context),
     * так как он становится известен только в середине процесса. Основная логика помещения
     * {@code userId} в MDC реализована в {@code JwtAuthenticationFilter} для уже
     * аутентифицированных запросов.
     * </p>
     *
     * @param request DTO с данными для регистрации. Не должен быть null.
     * @return {@link AuthResponse}, содержащий JWT.
     * @throws UserAlreadyExistsException если пользователь с таким email уже существует.
     * @throws PasswordMismatchException  если пароли в запросе не совпадают.
     */
    @Transactional
    public AuthResponse register(@NonNull RegisterRequest request) {
        if (!request.getPassword().equals(request.getRepeatPassword())) {
            throw new PasswordMismatchException();
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException(request.getEmail());
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        User savedUser = persistNewUser(user);

        log.info("User registration successful for email: {}. User ID: {}", savedUser.getEmail(), savedUser.getId());

        // Авто-логин после регистрации
        AppUserDetails userDetails = new AppUserDetails(savedUser);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null, // Пароль не нужен для уже аутентифицированного principal
                userDetails.getAuthorities()
        );


        // Генерируем JWT для этого Authentication
        String accessToken = jwtIssuer.generateToken(authentication);

        // TODO: Асинхронная отправка приветственного email (когда будет EmailSender и Kafka). transactional!

        long expirationSeconds = jwtProperties.getExpirationMs() / 1000;
        return new AuthResponse(accessToken, expirationSeconds);
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
        long expirationSeconds = jwtProperties.getExpirationMs() / 1000;
        return new AuthResponse(accessToken, expirationSeconds);
    }

    /**
     * Сохраняет нового пользователя в базе данных, выполняя немедленную синхронизацию с БД.
     * <p>
     * Этот метод предназначен для сохранения предварительно сконфигурированного объекта {@link User}.
     * Предполагается, что вызывающий код уже провел необходимые бизнес-валидации
     * (например, совпадение паролей для регистрации) и, возможно, предварительную проверку
     * на существование пользователя по email для оптимизации ("быстрый путь" отсечения).
     * </p>
     * <p>
     * Метод специфически обрабатывает {@link DataIntegrityViolationException}:
     * <ul>
     *     <li>Если исключение вызвано конфликтом уникальности email (что проверяется
     *         повторным запросом к БД), выбрасывается {@link UserAlreadyExistsException}.
     *         Это обычно указывает на состояние гонки, когда другой запрос успел создать
     *         пользователя с таким же email между предварительной проверкой и фактическим сохранением.</li>
     *     <li>Если {@link DataIntegrityViolationException} вызвана другой причиной (не дублированием email),
     *         это указывает на более серьезную, неожиданную проблему с целостностью данных или конфигурацией БД.
     *         В этом случае выбрасывается {@link IllegalStateException}, оборачивая оригинальное исключение,
     *         чтобы сигнализировать о критической ошибке, требующей расследования.</li>
     * </ul>
     * <p>
     * Выполняется в контексте существующей транзакции, если таковая имеется,
     * или создает новую, если вызывается без активной транзакции (в зависимости от
     * настроек транзакционности вызывающего метода).
     * </p>
     *
     * @param userToPersist Предварительно сконфигурированный объект {@code User} для сохранения.
     *                      Не должен быть {@code null}. Поля, такие как email и хешированный пароль,
     *                      должны быть уже установлены.
     * @return Сохраненный объект {@code User}.
     * @throws UserAlreadyExistsException если при сохранении возникает конфликт уникальности email,
     *                                    подтвержденный как состояние гонки.
     * @throws IllegalStateException если возникает неожиданная {@link DataIntegrityViolationException},
     *                               не связанная с дублированием email.
     * @throws NullPointerException если {@code userToPersist} равен {@code null}.
     */
    private User persistNewUser(@NonNull User userToPersist) {
        try {
            return userRepository.saveAndFlush(userToPersist);
        } catch (DataIntegrityViolationException e) {
            if (userRepository.existsByEmail(userToPersist.getEmail())) {
                log.warn("DataIntegrityViolationException during persistNewUser for email: {}. " +
                                "Confirmed email already exists (race condition). Converting to UserAlreadyExistsException.",
                        userToPersist.getEmail(), e);
                throw new UserAlreadyExistsException(userToPersist.getEmail(), e);
            } else {
                log.error("CRITICAL: Unexpected DataIntegrityViolationException during persistNewUser for email: {}. " +
                                "The email does NOT exist after the exception. This suggests an integrity issue NOT " +
                                "related to email duplication. Check database constraints for the 'users' table and " +
                                "application logic before this save operation.",
                        userToPersist.getEmail(), e);
                throw new IllegalStateException("Unexpected data integrity violation during user persistence", e);
            }
        }
    }
}