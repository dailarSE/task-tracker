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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис для операций регистрации и аутентификации пользователей.
 * <p>
 * Инкапсулирует бизнес-логику, связанную с созданием новых пользовательских аккаунтов
 * и проверкой учетных данных существующих пользователей. Взаимодействует с компонентами
 * для работы с базой данных ({@link UserRepository}), хеширования паролей ({@link PasswordEncoder}),
 * управления аутентификацией Spring Security ({@link AuthenticationManager}) и генерации
 * JWT ({@link JwtIssuer}).
 * </p>
 * <p>
 * Для корректной обработки состояний гонки при регистрации и избежания проблем
 * с транзакциями, помеченными как "rollback-only", сервис использует самоинъекцию
 * (self-injection) через {@link Lazy} для вызова некоторых методов в новой транзакции.
 * </p>
 */
@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtIssuer jwtIssuer;
    private final JwtProperties jwtProperties;
    private final AuthService self;

    /**
     * Конструктор {@link AuthService}.
     * <p>
     * Внедряет все необходимые зависимости для выполнения операций аутентификации и регистрации.
     * Параметр {@code self} внедряется с аннотацией {@link Lazy} для разрешения циклической
     * зависимости, возникающей при необходимости вызова методов этого же сервиса
     * в новой транзакционной границе (например, для {@link #existsByEmailInNewTransaction(String)}).
     * </p>
     *
     * @param userRepository      Репозиторий для доступа к данным пользователей.
     * @param passwordEncoder     Кодировщик паролей для хеширования и проверки.
     * @param authenticationManager Менеджер аутентификации Spring Security.
     * @param jwtIssuer           Сервис для генерации JWT.
     * @param jwtProperties       Конфигурационные свойства JWT.
     * @param self                Ленивая ссылка на экземпляр этого же сервиса для вызова
     *                            проксируемых методов в новых транзакциях.
     */
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtIssuer jwtIssuer,
                       JwtProperties jwtProperties,
                       @Lazy AuthService self) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtIssuer = jwtIssuer;
        this.jwtProperties = jwtProperties;
        this.self = self;
    }

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
     * <p>
     * Метод является транзакционным. Внутренний вызов для сохранения пользователя
     * ({@link #persistNewUser(User)}) обрабатывает потенциальные состояния гонки
     * при проверке уникальности email, используя вызов к {@link #existsByEmailInNewTransaction(String)}
     * в отдельной транзакции.
     * </p>
     *
     * @param request DTO с данными для регистрации. Не должен быть null.
     * @return {@link AuthResponse}, содержащий JWT.
     * @throws UserAlreadyExistsException если пользователь с таким email уже существует.
     * @throws PasswordMismatchException  если пароли в запросе не совпадают.
     * @throws NullPointerException если {@code request} равен {@code null}.
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
     * Предполагается, что вызывающий код (например, {@link #register(RegisterRequest)})
     * уже провел необходимые бизнес-валидации.
     * </p>
     * <p>
     * Метод специфически обрабатывает {@link DataIntegrityViolationException}, которое может возникнуть
     * из-за нарушения уникальности email (состояние гонки). Для корректной проверки существования email
     * после такого исключения (когда основная транзакция может быть помечена как rollback-only),
     * используется вызов {@link #existsByEmailInNewTransaction(String)} через самоинъекцию ({@code self}),
     * что обеспечивает выполнение проверки в новой, независимой транзакции.
     * </p>
     * <ul>
     *     <li>Если после {@link DataIntegrityViolationException} проверка в новой транзакции
     *         подтверждает существование email, выбрасывается {@link UserAlreadyExistsException}.</li>
     *     <li>Если email не существует (ошибка целостности не связана с дубликатом email),
     *         выбрасывается {@link IllegalStateException}.</li>
     * </ul>
     * <p>
     * Метод не аннотирован {@code @Transactional} сам по себе; ожидается, что он будет
     * вызываться из контекста существующей транзакции (например, из метода {@code register}).
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
            if (self.existsByEmailInNewTransaction(userToPersist.getEmail())) {
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

    /**
     * Проверяет существование пользователя по email в новой транзакции.
     * Это необходимо, чтобы избежать проблем при проверке после DataIntegrityViolationException
     * в основной транзакции, которая может быть помечена как rollback-only.
     *
     * @param email Email для проверки.
     * @return true, если пользователь с таким email существует, иначе false.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean existsByEmailInNewTransaction(@NonNull String email) {
        return userRepository.existsByEmail(email);
    }
}