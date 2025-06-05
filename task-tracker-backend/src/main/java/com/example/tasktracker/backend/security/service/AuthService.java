package com.example.tasktracker.backend.security.service;

import com.example.tasktracker.backend.common.MdcKeys;
import com.example.tasktracker.backend.kafka.service.EmailNotificationOrchestratorService;
import com.example.tasktracker.backend.security.details.AppUserDetails;
import com.example.tasktracker.backend.security.dto.AuthResponse;
import com.example.tasktracker.backend.security.dto.LoginRequest;
import com.example.tasktracker.backend.security.dto.RegisterRequest;
import com.example.tasktracker.backend.security.exception.PasswordMismatchException;
import com.example.tasktracker.backend.security.exception.UserAlreadyExistsException;
import com.example.tasktracker.backend.security.jwt.JwtIssuer;
import com.example.tasktracker.backend.security.jwt.JwtProperties;
import com.example.tasktracker.backend.user.entity.User;
import com.example.tasktracker.backend.user.messaging.dto.EmailTriggerCommand;
import com.example.tasktracker.backend.user.repository.UserRepository;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

/** Сервис для операций регистрации и аутентификации пользователей. */
@Service
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final EmailNotificationOrchestratorService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtIssuer jwtIssuer;
    private final JwtProperties jwtProperties;
    private final AuthService self;

    /**
     * Конструктор для {@link AuthService}.
     * <p>
     * Внедряет все необходимые зависимости. Параметр {@code self} используется
     * для вызова методов этого же сервиса с иной транзакционной семантикой
     * (например, {@link Propagation#REQUIRES_NEW}).
     * </p>
     *
     * @param userRepository        Репозиторий пользователей.
     * @param notificationService  Сервис для отправки сообщений в Kafka.
     * @param passwordEncoder       Кодировщик паролей.
     * @param authenticationManager Менеджер аутентификации Spring Security.
     * @param jwtIssuer             Сервис для генерации JWT.
     * @param jwtProperties         Конфигурационные свойства JWT.
     * @param self                  Ленивая ссылка на экземпляр этого же сервиса.
     */
    public AuthService(UserRepository userRepository,
                       EmailNotificationOrchestratorService notificationService,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtIssuer jwtIssuer,
                       JwtProperties jwtProperties,
                       @Lazy AuthService self) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtIssuer = jwtIssuer;
        this.jwtProperties = jwtProperties;
        this.self = self;
    }

    /**
     * Регистрирует нового пользователя в системе на основе предоставленных данных.
     * <p>
     * Процесс включает:
     * <ol>
     *     <li>Проверку совпадения паролей.</li>
     *     <li>Проверку уникальности email.</li>
     *     <li>Хеширование пароля.</li>
     *     <li>Сохранение нового пользователя в базу данных (см. {@link #persistNewUser(User)}).</li>
     *     <li>Установку ID пользователя в MDC для последующего логирования (см. {@link MdcKeys#USER_ID}).</li>
     *     <li>Инициацию отправки приветственного Kafka-сообщения (см. {@link #initiateWelcomeNotification(User)}).</li>
     *     <li>Автоматическую аутентификацию пользователя.</li>
     *     <li>Генерацию и возврат JWT Access Token.</li>
     * </ol>
     * Метод является транзакционным.
     * </p>
     *
     * @param request DTO {@link RegisterRequest} с данными для регистрации. Не должен быть null.
     * @return {@link AuthResponse}, содержащий JWT Access Token и информацию о нем.
     * @throws UserAlreadyExistsException если пользователь с таким email уже существует.
     * @throws PasswordMismatchException  если пароли в запросе на регистрацию не совпадают.
     * @throws NullPointerException       если {@code request} равен {@code null}.
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
        Long savedUserId = savedUser.getId();

        try (MDC.MDCCloseable ignored = MDC.putCloseable(MdcKeys.USER_ID, String.valueOf(savedUserId))) {
            log.info("User registration successful. User ID: {} added to MDC. Email: {}", savedUserId, savedUser.getEmail());

            initiateWelcomeNotification(savedUser);

            AppUserDetails userDetails = new AppUserDetails(savedUser);
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );

            String accessToken = jwtIssuer.generateToken(authentication);
            long expirationSeconds = jwtProperties.getExpirationMs() / 1000;
            return new AuthResponse(accessToken, expirationSeconds);
        }
    }

    /**
     * Инициирует отправку приветственного уведомления в Kafka для нового пользователя.
     * Ошибки при отправке в Kafka логируются, но не прерывают основной процесс.
     *
     * @param newUser Только что сохраненная сущность {@link User}.
     */
    void initiateWelcomeNotification(@NonNull User newUser) {
        try {
            EmailTriggerCommand emailCommand = createEmailTriggerCommand(newUser);
            notificationService.scheduleInitialEmailNotification(emailCommand);
            log.info("Welcome notification initiation for userId: {} (email: {}) sent to Kafka queue.",
                    newUser.getId(), newUser.getEmail());
        } catch (Exception e) {
            // Логируем ошибку инициации отправки в Kafka, но НЕ прерываем основной процесс
            log.error("Failed to initiate Kafka message for user registration (userId: {}). " +
                            "Registration process will continue. Error: {}",
                    newUser.getId(), e.getMessage(), e);
        }
    }

    /**
     * Формирует команду {@link EmailTriggerCommand} для отправки приветственного email.
     * <p>
     * Этот метод извлекает текущую локаль из {@link LocaleContextHolder} и
     * подготавливает контекст для шаблона приветственного письма.
     * </p>
     *
     * @param newUser Сущность {@link User}, для которой формируется команда. Не должна быть {@code null}.
     * @return Сконфигурированный объект {@link EmailTriggerCommand}.
     * @throws NullPointerException если {@code newUser} равен {@code null}.
     */
    EmailTriggerCommand createEmailTriggerCommand(@NonNull User newUser) {
        Locale currentUserLocale = LocaleContextHolder.getLocale();
        String localeTag = currentUserLocale.toLanguageTag();

        return new EmailTriggerCommand(
                newUser.getEmail(),
                "USER_WELCOME",
                Map.of("userEmail", newUser.getEmail()),
                localeTag,
                String.valueOf(newUser.getId()), // userId
                null // correlationId будет установлен в kafka service
        );
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
     * @throws IllegalStateException      если возникает неожиданная {@link DataIntegrityViolationException},
     *                                    не связанная с дублированием email.
     * @throws NullPointerException       если {@code userToPersist} равен {@code null}.
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