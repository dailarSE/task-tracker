package com.example.tasktracker.scheduler.job;

import com.example.tasktracker.scheduler.job.dto.JobExecutionState;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.NonNull;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Интерфейс репозитория для управления персистентным состоянием джоб.
 */
public interface JobStateRepository {

    /**
     * Находит состояние джобы для указанного имени и даты.
     *
     * @param jobName Имя джобы.
     * @param date    Дата, для которой ищется состояние.
     * @param typeReference TypeReference для десериализации generic payload'а.
     * @return Optional с состоянием джобы, если найдено.
     */
    <T> Optional<JobExecutionState<T>> findState(
            @NonNull String jobName,
            @NonNull LocalDate date,
            @NonNull TypeReference<JobExecutionState<T>> typeReference
    );

    /**
     * Сохраняет состояние джобы.
     *
     * @param jobName Имя джобы.
     * @param date    Дата.
     * @param state   Состояние для сохранения.
     */
    <T> void saveState(
            @NonNull String jobName,
            @NonNull LocalDate date,
            @NonNull JobExecutionState<T> state
    );
}