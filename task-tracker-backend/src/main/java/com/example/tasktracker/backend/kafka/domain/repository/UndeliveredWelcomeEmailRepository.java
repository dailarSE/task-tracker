package com.example.tasktracker.backend.kafka.domain.repository;

import com.example.tasktracker.backend.kafka.domain.entity.UndeliveredWelcomeEmail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для управления сущностями {@link UndeliveredWelcomeEmail}.
 * <p>
 * Предоставляет стандартные CRUD-операции для сохранения и извлечения
 * информации о неотправленных в Kafka командах на email.
 * </p>
 */
@Repository
public interface UndeliveredWelcomeEmailRepository extends JpaRepository<UndeliveredWelcomeEmail, Long> {
    // На данном этапе стандартных методов JpaRepository достаточно.
    // В будущем могут быть добавлены методы для поиска команд для переотправки.
}