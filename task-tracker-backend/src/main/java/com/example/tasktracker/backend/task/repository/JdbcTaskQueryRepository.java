package com.example.tasktracker.backend.task.repository;

import com.example.tasktracker.backend.internal.scheduler.dto.TaskInfo;
import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

/**
 * JDBC-реализация {@link TaskQueryRepository}.
 * <p>
 * Использует {@link org.springframework.jdbc.core.simple.JdbcClient} и нативный,
 * оптимизированный для PostgreSQL SQL-запрос для эффективной агрегации данных.
 * </p>
 */
@Repository
@Slf4j
public class JdbcTaskQueryRepository implements TaskQueryRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<UserTaskReport> userTaskReportRowMapper;

    public JdbcTaskQueryRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.userTaskReportRowMapper = new UserTaskReportRowMapper(objectMapper);
    }

    /**
     * <p>
     * **Логика агрегации, заложенная в SQL-запросе:**
     * <ul>
     *     <li>Для невыполненных задач (PENDING): выбираются до 5 самых старых (по дате создания).</li>
     *     <li>Для выполненных задач (COMPLETED): выбираются все задачи, завершенные в интервале [from, to).</li>
     * </ul>
     * Вся агрегация происходит на стороне БД с использованием {@code jsonb_agg} для
     * минимизации нагрузки на приложение и количества запросов.
     * </p>
     */
    @Override
    public List<UserTaskReport> generateTaskReportsForUsers(@NonNull List<Long> userIds, @NonNull Instant from, @NonNull Instant to) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyList();
        }

        final String sql = """
                WITH user_ids_to_process AS (
                    SELECT id FROM unnest(:userIdsArray::bigint[]) AS u(id)
                ),
                ranked_tasks AS (
                    SELECT
                        t.user_id,
                        t.id AS task_id,
                        t.title AS task_title,
                        t.status,
                        t.created_at,
                        t.completed_at,
                        CASE
                            WHEN t.status = 'PENDING' THEN ROW_NUMBER() OVER (PARTITION BY t.user_id ORDER BY t.created_at ASC)
                            ELSE NULL
                        END as pending_rank
                    FROM tasks t
                    WHERE
                        EXISTS (SELECT 1 FROM user_ids_to_process u WHERE u.id = t.user_id)
                      AND
                        (t.status = 'PENDING' OR (t.status = 'COMPLETED' AND t.completed_at >= :from AND t.completed_at < :to))
                ),
                aggregated_reports AS (
                    SELECT
                        user_id,
                        jsonb_agg(jsonb_build_object('id', task_id, 'title', task_title) ORDER BY created_at ASC)
                            FILTER (WHERE status = 'PENDING' AND pending_rank <= 5) AS pending_tasks,
                        jsonb_agg(jsonb_build_object('id', task_id, 'title', task_title) ORDER BY completed_at DESC)
                            FILTER (WHERE status = 'COMPLETED') AS completed_tasks
                    FROM ranked_tasks
                    GROUP BY user_id
                )
                SELECT
                    ar.user_id,
                    u.email,
                    COALESCE(ar.pending_tasks, '[]'::jsonb) AS pendingTasks,
                    COALESCE(ar.completed_tasks, '[]'::jsonb) AS completedTasks
                FROM
                    aggregated_reports ar
                JOIN
                    users u ON ar.user_id = u.id
                """;

        return jdbcClient.sql(sql)
                .param("userIdsArray", userIds.toArray(new Long[0]))
                .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                .param("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                .query(userTaskReportRowMapper)
                .list();
    }

    private static class UserTaskReportRowMapper implements RowMapper<UserTaskReport> {
        private final ObjectMapper objectMapper;
        private static final TypeReference<List<TaskInfo>> TASK_INFO_LIST_TYPE_REF = new TypeReference<>() {};

        public UserTaskReportRowMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public UserTaskReport mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            Long userId = rs.getLong("user_id");
            String email = rs.getString("email");
            String pendingJson = rs.getString("pendingTasks");
            String completedJson = rs.getString("completedTasks");

            try {
                List<TaskInfo> pendingTasks = parseTasks(userId, pendingJson, "pendingTasks");
                List<TaskInfo> completedTasks = parseTasks(userId, completedJson, "completedTasks");
                return new UserTaskReport(userId, email, completedTasks, pendingTasks);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse TaskInfo JSON from database for user ID: {}. Invalid JSON in ResultSet.", userId, e);
                throw new SQLException("Failed to parse TaskInfo JSON from database for user ID: " + userId, e);
            }
        }

        private List<TaskInfo> parseTasks(Long userId, String json, String fieldName) throws JsonProcessingException {
            if (json == null) {
                log.warn("Unexpected null value for JSON field '{}' for user ID: {}. " +
                        "Expected '[]' due to COALESCE in SQL. Defaulting to empty list.", fieldName, userId);
                return Collections.emptyList();
            }
            return objectMapper.readValue(json, TASK_INFO_LIST_TYPE_REF);
        }
    }
}