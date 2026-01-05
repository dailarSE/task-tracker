package com.example.tasktracker.scheduler.job;

import lombok.NonNull;

import java.time.LocalDate;

/**
 * Интерфейс, определяющий контракт для генерации ключей,
 * используемых для хранения состояния джоб в персистентном хранилище.
 */
public interface JobStateKeyProvider {

    /**
     * Формирует основной ключ (например, ключ HASH'а в Redis),
     * идентифицирующий хранилище состояний для конкретной джобы.
     *
     * @param jobName Уникальное имя джобы.
     * @return Строка, представляющая основной ключ.
     */
    String getHashKey(@NonNull String jobName);

    /**
     * Формирует ключ поля (например, поле внутри HASH'а в Redis),
     * идентифицирующий состояние для конкретной даты.
     *
     * @param date Дата, для которой формируется ключ.
     * @return Строка, представляющая ключ поля.
     */
    String getHashField(@NonNull LocalDate date);
}