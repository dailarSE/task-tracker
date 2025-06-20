package com.example.tasktracker.backend.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Реализация {@link UserQueryRepository} с использованием JdbcTemplate для оптимального выполнения запросов.
 */
@Repository
@RequiredArgsConstructor
public class JdbcUserQueryRepository implements UserQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * {@inheritDoc}
     */
    @Override
    public Slice<Long> findUserIds(long lastProcessedId, int limit) {
        final int queryLimit = limit + 1;

        final String sql = """
                SELECT id
                FROM users
                WHERE id > ?
                ORDER BY id ASC
                LIMIT ?
                """;

        List<Long> ids = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("id"),
                lastProcessedId,
                queryLimit
        );

        boolean hasNext = ids.size() > limit;
        List<Long> content = hasNext ? ids.subList(0, limit) : ids;

        // Slice не имеет информации об общем количестве страниц, поэтому номер страницы (0) здесь условный.
        return new SliceImpl<>(content, PageRequest.of(0, limit), hasNext);
    }
}