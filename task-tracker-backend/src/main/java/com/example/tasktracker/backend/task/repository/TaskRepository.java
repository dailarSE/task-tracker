package com.example.tasktracker.backend.task.repository;

import com.example.tasktracker.backend.task.entity.Task;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для управления сущностями {@link Task}.
 * Реализует "безопасные по умолчанию" методы доступа к данным,
 * требующие идентификатор пользователя для большинства операций.
 * Не наследует стандартные интерфейсы Spring Data JPA, чтобы обеспечить
 * явное определение всех разрешенных операций.
 */
public interface TaskRepository extends Repository<Task, Long> {

    /**
     * Находит задачу по ее ID и ID пользователя-владельца.
     *
     * @param taskId ID задачи.
     * @param userId ID пользователя.
     * @return {@link Optional} с найденной задачей или пустой, если не найдена или не принадлежит пользователю.
     */
    @Query("SELECT t FROM Task t WHERE t.id = :taskId AND t.user.id = :userId")
    Optional<Task> findByIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);

    /**
     * Находит все задачи для указанного пользователя с пагинацией.
     * Задачи сортируются по умолчанию (можно переопределить через Pageable).
     *
     * @param userId ID пользователя.
     * @param pageable Параметры пагинации и сортировки.
     * @return Страница ({@link Page}) с задачами пользователя.
     */
    @Query("SELECT t FROM Task t WHERE t.user.id = :userId")
    Page<Task> findAllByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Находит все задачи для указанного пользователя, отсортированные по времени создания (новые сначала).
     *
     * @param userId ID пользователя.
     * @return Список задач пользователя, отсортированный по createdAt DESC.
     */
    @Query("SELECT t FROM Task t WHERE t.user.id = :userId ORDER BY t.createdAt DESC")
    List<Task> findAllByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    /**
     * Находит все задачи для указанного пользователя с определенным статусом и пагинацией.
     *
     * @param userId ID пользователя.
     * @param status Статус задачи для фильтрации.
     * @param pageable Параметры пагинации и сортировки.
     * @return Страница ({@link Page}) с задачами пользователя, отфильтрованными по статусу.
     */
    @Query("SELECT t FROM Task t WHERE t.user.id = :userId AND t.status = :status")
    Page<Task> findAllByUserIdAndStatus(@Param("userId") Long userId, @Param("status") TaskStatus status, Pageable pageable);

    /**
     * Сохраняет задачу (новую или существующую).
     *
     * @param task Сущность задачи для сохранения.
     * @return Сохраненная сущность задачи (может содержать сгенерированный ID).
     */
    Task save(Task task);


    /**
     * Сохраняет задачу и немедленно синхронизирует изменения с базой данных.
     * <p>
     * Этот метод аналогичен {@link #save(Task)}, но дополнительно выполняет операцию flush,
     * что принудительно отправляет все ожидающие SQL-команды в базу данных.
     * Это может быть полезно в сценариях, где необходимо немедленно увидеть результат
     * операции в БД (например, в тестах или при сложных транзакционных взаимодействиях).
     * </p>
     *
     * @param task Сущность задачи для сохранения и синхронизации. Не должна быть {@code null}.
     * @return Сохраненная и синхронизированная сущность задачи.
     * @see #save(Task)
     */
    Task saveAndFlush(Task task);


    /**
     * Удаляет задачу по ее ID, если она принадлежит указанному пользователю.
     *
     * @param taskId ID задачи для удаления.
     * @param userId ID пользователя-владельца.
     * @return Количество удаленных записей (0 или 1).
     */
    @Modifying
    @Query("DELETE FROM Task t WHERE t.id = :taskId AND t.user.id = :userId")
    int deleteByIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);

    /**
     * Проверяет существование задачи по ее ID и ID пользователя-владельца.
     *
     * @param taskId ID задачи.
     * @param userId ID пользователя.
     * @return {@code true} если задача существует и принадлежит пользователю, иначе {@code false}.
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Task t " +
            "WHERE t.id = :taskId AND t.user.id = :userId")
    boolean existsByIdAndUserId(@Param("taskId") Long taskId, @Param("userId") Long userId);
}