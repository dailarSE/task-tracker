package com.example.tasktracker.backend.kafka.domain.repository;

import com.example.tasktracker.backend.kafka.domain.entity.UndeliveredEmailCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для управления сущностями {@link UndeliveredEmailCommand}.
 * <p>
 * Предоставляет стандартные CRUD-операции для сохранения и извлечения
 * информации о неотправленных в Kafka командах на email.
 * </p>
 */
@Repository
public interface UndeliveredEmailCommandRepository extends JpaRepository<UndeliveredEmailCommand, Long> {
    // На данном этапе стандартных методов JpaRepository достаточно.
    // В будущем могут быть добавлены методы для поиска команд для переотправки.
}