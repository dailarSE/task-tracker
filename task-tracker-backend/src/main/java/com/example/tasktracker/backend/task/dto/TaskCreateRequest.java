package com.example.tasktracker.backend.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DTO (Data Transfer Object) для запроса на создание новой задачи.
 * Содержит данные, необходимые для создания задачи.
 */
@Getter
@AllArgsConstructor
public class TaskCreateRequest {

    /**
     * Заголовок задачи. Обязательное поле.
     * Максимальная длина: 255 символов.
     * Сообщение об ошибке валидации извлекается из Resource Bundle по ключу.
     */
    @NotBlank(message = "{task.dto.create.title.notBlank}")
    @Size(max = 255, message = "{task.dto.create.title.size}")
    private final String title;

    /**
     * Описание задачи. Опциональное поле.
     * Максимальная длина: 1000 символов.
     * Сообщение об ошибке валидации извлекается из Resource Bundle по ключу.
     */
    @Size(max = 1000, message = "{task.dto.create.description.size}")
    private final String description;
}