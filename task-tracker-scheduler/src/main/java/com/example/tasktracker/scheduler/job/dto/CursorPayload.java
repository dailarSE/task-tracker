package com.example.tasktracker.scheduler.job.dto;

import org.springframework.lang.Nullable;

/**
 * Payload для джоб, использующих пагинацию по курсору.
 *
 * @param lastCursor Непрозрачный курсор для следующей итерации.
 */
public record CursorPayload(@Nullable String lastCursor) {}