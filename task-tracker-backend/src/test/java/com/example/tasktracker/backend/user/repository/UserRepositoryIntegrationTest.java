package com.example.tasktracker.backend.user.repository;

import com.example.tasktracker.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Testcontainers
@ActiveProfiles("ci")
class UserRepositoryIntegrationTest {
    @Autowired
    private UserRepository userRepository;
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgresContainer=
            new PostgreSQLContainer<>("postgres:17.4-alpine");


    @Test
    @DisplayName("Сохранение нового пользователя должно быть успешным и все поля должны быть установлены")
    void whenSaveNewUser_thenSuccess() {
        User user = new User(null, "test@example.com", "password123", null, null);
        User savedUser = userRepository.save(user);

        assertThat(savedUser).isNotNull();
        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.getPassword()).isEqualTo("password123"); // Проверим и пароль
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
        // Проверка, что createdAt и updatedAt примерно равны (с дельтой для возможных задержек)
        assertThat(savedUser.getUpdatedAt()).isCloseTo(savedUser.getCreatedAt(), within(50, ChronoUnit.MILLIS));
    }

    @Test
    @DisplayName("Поиск существующего пользователя по email должен вернуть пользователя")
    void testSaveAndFindByEmail_whenUserExists_thenReturnUser() {
        User userToSave = new User(null, "findme@example.com", "securepass", null, null);
        userRepository.save(userToSave);

        Optional<User> foundUserOptional = userRepository.findByEmail("findme@example.com");

        assertThat(foundUserOptional).isPresent();
        foundUserOptional.ifPresent(foundUser -> {
            assertThat(foundUser.getEmail()).isEqualTo("findme@example.com");
            assertThat(foundUser.getId()).isEqualTo(userToSave.getId()); // Убедимся, что это тот же пользователь
        });
    }

    @Test
    @DisplayName("Поиск несуществующего пользователя по email должен вернуть пустой Optional")
    void testFindByEmail_whenUserDoesNotExist_thenReturnEmpty() {
        Optional<User> foundUserOptional = userRepository.findByEmail("nonexistent@example.com");

        assertThat(foundUserOptional).isEmpty();
    }

    @Test
    @DisplayName("Проверка существования пользователя по email должна вернуть true, если пользователь существует")
    void testExistsByEmail_whenUserExists_thenReturnTrue() {
        User userToSave = new User(null, "exists@example.com", "password", null, null);
        userRepository.save(userToSave);

        boolean exists = userRepository.existsByEmail("exists@example.com");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Проверка существования пользователя по email должна вернуть false, если пользователь не существует")
    void testExistsByEmail_whenUserDoesNotExist_thenReturnFalse() {
        boolean exists = userRepository.existsByEmail("notexists@example.com");

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("При обновлении пользователя должно обновиться только поле updatedAt")
    void testUpdateUser_shouldUpdateUpdatedAtAndKeepCreatedAt() {
        // 1. Сохраняем пользователя
        User user = new User(null, "update@example.com", "initialPassword", null, null);
        // Используем saveAndFlush, чтобы createdAt и updatedAt точно записались в БД
        User savedUser = userRepository.saveAndFlush(user);

        // Получаем свежие значения из БД после первого сохранения
        User persistedUserAfterFirstSave = userRepository.findById(savedUser.getId()).orElseThrow();
        Instant initialCreatedAt = persistedUserAfterFirstSave.getCreatedAt();
        Instant initialUpdatedAt = persistedUserAfterFirstSave.getUpdatedAt();

        assertThat(initialCreatedAt).isNotNull();
        assertThat(initialUpdatedAt).isNotNull();

        // 2. Небольшая пауза, чтобы гарантировать, что новый timestamp будет отличаться
        // Этот шаг все еще полезен, чтобы время точно "продвинулось"
        try {
            Thread.sleep(50); // Увеличьте, если 50мс недостаточно на вашей системе/БД
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        // 3. Загружаем пользователя СНОВА (чтобы получить свежую копию, если мы не очищали кэш),
        //    ИЛИ работаем с уже загруженным, если уверены, что кэш не помешает.
        //    Для большей надежности можно загрузить снова, но так как @DataJpaTest
        //    и @Transactional работают с сессией, userToUpdate будет управляемым.
        User userToUpdate = userRepository.findById(savedUser.getId()).orElseThrow();
        userToUpdate.setPassword("newPassword"); // Изменяем какое-либо поле

        // Используем saveAndFlush, чтобы @UpdateTimestamp сработал и изменения ушли в БД
        User updatedUserFromSave = userRepository.saveAndFlush(userToUpdate);

        // 4. Загружаем финальную версию из БД для проверки
        User finalUpdatedUser = userRepository.findById(updatedUserFromSave.getId()).orElseThrow();

        // 5. Проверяем временные метки
        assertThat(finalUpdatedUser.getCreatedAt()).isEqualTo(initialCreatedAt); // createdAt не должен измениться
        assertThat(finalUpdatedUser.getUpdatedAt()).isNotNull();

        // Основная проверка: updatedAt должен быть ПОСЛЕ или РАВЕН начальному updatedAt.
        // И СТРОГО ПОСЛЕ, если изменения действительно произошли и время сдвинулось.
        assertThat(finalUpdatedUser.getUpdatedAt()).isAfter(initialUpdatedAt);
    }

    @Test
    @DisplayName("Попытка сохранить пользователя с дублирующимся email должна вызвать DataIntegrityViolationException")
    void testSaveUserWithDuplicateEmail_shouldThrowException() {
        User user1 = new User(null, "duplicate@example.com", "password123", null, null);
        userRepository.save(user1);

        User user2 = new User(null, "duplicate@example.com", "anotherPassword", null, null);

        // Проверяем, что выбрасывается исключение
        assertThatThrownBy(() -> userRepository.saveAndFlush(user2)) // saveAndFlush для немедленной отправки в БД
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Удаление пользователя должно работать корректно")
    void testDeleteUser_shouldRemoveUserFromDatabase() {
        User user = new User(null, "todelete@example.com", "password", null, null);
        User savedUser = userRepository.save(user);
        Long userId = savedUser.getId();

        assertThat(userRepository.existsById(userId)).isTrue();

        userRepository.deleteById(userId);

        assertThat(userRepository.existsById(userId)).isFalse();
        assertThat(userRepository.findByEmail("todelete@example.com")).isEmpty();
    }

    @Test
    @DisplayName("deleteAll должен удалить всех пользователей")
    void testDeleteAll_shouldRemoveAllUsers() {
        userRepository.save(new User(null, "user1@example.com", "pass1", null, null));
        userRepository.save(new User(null, "user2@example.com", "pass2", null, null));

        assertThat(userRepository.count()).isEqualTo(2);

        userRepository.deleteAll();

        assertThat(userRepository.count()).isEqualTo(0);
    }
}