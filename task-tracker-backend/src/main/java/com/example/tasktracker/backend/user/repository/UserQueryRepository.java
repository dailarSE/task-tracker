package com.example.tasktracker.backend.user.repository;

import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для выполнения сложных или специфичных "читающих" запросов,
 * связанных с пользователями, которые не являются частью стандартного CRUD.
 */
@Repository
public interface UserQueryRepository {

    /**
     * Находит порцию ID пользователей для обработки, используя keyset-пагинацию.
     *
     * @param lastProcessedId ID последнего обработанного пользователя. Запрос выберет пользователей с ID > этого значения.
     * @param limit           Максимальное количество ID для возврата.
     * @return Слайс (Slice) с ID пользователей.
     */
    Slice<Long> findUserIds(long lastProcessedId, int limit);
}