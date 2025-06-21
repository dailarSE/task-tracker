package com.example.tasktracker.backend.task.repository;

import com.example.tasktracker.backend.internal.scheduler.dto.TaskInfo;
import com.example.tasktracker.backend.internal.scheduler.dto.UserTaskReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class JdbcTaskQueryRepository implements TaskQueryRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<UserTaskReport> findTaskReportsForUsers(@NonNull List<Long> userIds, @NonNull Instant from, @NonNull Instant to) {
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

        String idsArray = userIds.stream().map(String::valueOf).collect(Collectors.joining(",","{","}"));

        return jdbcClient.sql(sql)
                .param("userIdsArray", idsArray)
                .param("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                .param("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                .query(new UserTaskReportRowMapper(objectMapper))
                .list();
    }

    private static class UserTaskReportRowMapper implements RowMapper<UserTaskReport> {
        private final ObjectMapper objectMapper;
        private final TypeReference<List<TaskInfo>> taskInfoListTypeRef = new TypeReference<>() {};

        public UserTaskReportRowMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public UserTaskReport mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            Long userId = rs.getLong("user_id");
            String email = rs.getString("email");

            UserTaskReport report = new UserTaskReport(userId,email);

            try {
                List<TaskInfo> pending = objectMapper.readValue(rs.getString("pendingTasks"), taskInfoListTypeRef);
                List<TaskInfo> completed = objectMapper.readValue(rs.getString("completedTasks"), taskInfoListTypeRef);

                if (pending != null) {
                    report.getTasksPending().addAll(pending);
                }
                if (completed != null) {
                    report.getTasksCompleted().addAll(completed);
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to parse TaskInfo JSON from database for user ID: {}. Invalid JSON in ResultSet.", userId, e);
                throw new SQLException("Failed to parse TaskInfo JSON from database for user ID: " + userId, e);
            }
            return report;
        }
    }
}