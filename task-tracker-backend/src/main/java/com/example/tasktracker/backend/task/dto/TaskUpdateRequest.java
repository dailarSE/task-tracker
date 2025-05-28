package com.example.tasktracker.backend.task.dto;

import com.example.tasktracker.backend.task.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for updating an existing task.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskUpdateRequest {

    /**
     * The new title for the task. Must not be blank.
     * Max length: 255 characters.
     */
    @NotBlank(message = "{task.validation.title.notBlank}")
    @Size(max = 255, message = "{task.validation.title.size}")
    private String title;

    /**
     * The new description for the task. Can be null.
     * Max length: 1000 characters.
     */
    @Size(max = 1000, message = "{task.validation.description.size}")
    private String description;

    /**
     * The new status for the task. Must not be null.
     */
    @NotNull(message = "{task.validation.status.notNull}")
    private TaskStatus status;
}