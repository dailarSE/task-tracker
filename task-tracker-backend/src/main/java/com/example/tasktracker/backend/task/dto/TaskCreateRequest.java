package com.example.tasktracker.backend.task.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "DTO для запроса на создание новой задачи")
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
    @Schema(description = "Заголовок задачи.", example = "Купить молоко")
    @NotBlank(message = "{task.validation.title.notBlank}")
    @Size(max = 255, message = "{task.validation.title.size}")
    private String title;

    /**
     * Описание задачи. Опциональное поле.
     * Максимальная длина: 1000 символов.
     * Сообщение об ошибке валидации извлекается из Resource Bundle по ключу.
     */
    @Schema(description = "Детальное описание задачи.", example = "Обязательно 2.5% жирности")
    @Size(max = 1000, message = "{task.validation.description.size}")
    private String description;
}