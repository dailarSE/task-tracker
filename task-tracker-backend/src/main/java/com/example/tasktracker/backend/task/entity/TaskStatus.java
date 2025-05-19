package com.example.tasktracker.backend.task.entity;

/**
 * Перечисление, представляющее статус задачи.
 */
public enum TaskStatus {
    /**
     * Задача ожидает выполнения.
     */
    PENDING,
    /**
     * Задача выполнена.
     */
    COMPLETED
}