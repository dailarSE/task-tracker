package com.example.tasktracker.backend.task.dto;

import com.example.tasktracker.backend.task.entity.Task;
import com.example.tasktracker.backend.task.entity.TaskStatus;
import com.example.tasktracker.backend.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;

/**
 * DTO (Data Transfer Object) для представления задачи в ответах API.
 * Содержит полную информацию о задаче.
 */
@Schema(description = "DTO для представления задачи в ответах API")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    /**
     * Уникальный идентификатор задачи.
     */
    @Schema(description = "Уникальный идентификатор задачи.", example = "42")
    private Long id;

    /**
     * Заголовок задачи.
     */
    @Schema(description = "Заголовок задачи.", example = "Позвонить маме")
    private String title;

    /**
     * Описание задачи (может быть null).
     */
    @Schema(description = "Описание задачи.", example = "В 19:00, не забыть спросить про кота", nullable = true)
    private String description;

    /**
     * Статус задачи.
     */
    @Schema(description = "Статус задачи.", example = "PENDING")
    private TaskStatus status;

    /**
     * Временная метка создания задачи.
     */
    @Schema(description = "Временная метка создания задачи (UTC).", example = "2025-05-20T10:00:00Z")
    private Instant createdAt;

    /**
     * Временная метка последнего обновления задачи.
     */
    @Schema(description = "Временная метка последнего обновления задачи (UTC).", example = "2025-05-20T10:15:00Z")
    private Instant updatedAt;

    /**
     * Временная метка завершения задачи (может быть null).
     */
    @Schema(description = "Временная метка завершения задачи (UTC). Null, если задача не завершена.", nullable = true,
            example = "2025-05-20T10:15:00Z")
    Instant completedAt;

    /**
     * Версия сущности для оптимистической блокировки.
     * Клиент должен передавать это значение при обновлении задачи.
     */
    @Schema(description = "Версия сущности для оптимистической блокировки.", example = "0")
    private Integer version;

    /**
     * Идентификатор пользователя, которому принадлежит задача.
     */
    @Schema(description = "Идентификатор пользователя, которому принадлежит задача.", example = "1")
    private Long userId;

    /**
     * Статический фабричный метод для преобразования сущности {@link Task} в {@link TaskResponse}.
     * <p>
     * <strong>Предполагается, что переданная сущность {@code Task} уже прошла
     * все необходимые этапы валидации (например, через аннотации Jakarta Bean Validation
     * при сохранении в базу данных или через явную валидацию в сервисном слое)
     * и является консистентной.</strong>
     * </p>
     * <p>
     * Этот метод извлекает ID пользователя из связанной сущности {@link User}.
     * Он ожидает, что {@code task.getUser()} не вернет {@code null}, и что
     * {@code task.getUser().getId()} также не будет {@code null}, так как поле {@code user}
     * в {@link Task} является обязательным и у сохраненного пользователя всегда есть ID.
     * </p>
     *
     * @param task Сущность задачи, которая уже была сохранена в базу данных или, по крайней мере,
     *             полностью и корректно сформирована. Не должна быть {@code null}.
     * @return Объект {@link TaskResponse}.
     * @throws NullPointerException если {@code task} равен {@code null}.
     * @throws IllegalStateException если {@code task.getUser()} равен {@code null} или
     *                               {@code task.getUser().getId()} равен {@code null},
     *                               что указывает на неконсистентное состояние переданной сущности {@code Task}.
     */
    public static TaskResponse fromEntity(@NonNull Task task) {
        if (task.getUser() == null || task.getUser().getId() == null) {
            throw new IllegalStateException("Task entity (ID: " + task.getId() + ") is missing a valid associated User " +
                    "or User ID for mapping to TaskResponse.");
        }
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getCompletedAt(),
                task.getVersion(),
                task.getUser().getId()
        );
    }
}