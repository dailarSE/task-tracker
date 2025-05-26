package com.example.tasktracker.backend.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO (Data Transfer Object) для запроса на создание новой задачи.
 * Содержит данные, необходимые для создания задачи.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TaskCreateRequest {

    /**
     * Заголовок задачи. Обязательное поле.
     * Максимальная длина: 255 символов.
     * Сообщение об ошибке валидации извлекается из Resource Bundle по ключу.
     */
    @NotBlank(message = "{task.validation.title.notBlank}")
    @Size(max = 255, message = "{task.validation.title.size}")
    private String title;

    /**
     * Описание задачи. Опциональное поле.
     * Максимальная длина: 1000 символов.
     * Сообщение об ошибке валидации извлекается из Resource Bundle по ключу.
     */
    @Size(max = 1000, message = "{task.validation.description.size}")
    private String description;
}