package com.example.tasktracker.emailsender.pipeline.dispatch;

import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;

import java.util.concurrent.CompletableFuture;

/**
 * Абстрактный мост для переноса сообщения из пакетного контекста
 * в одиночный неблокирующий конвейер ретраев.
 */
public interface RetryHandOffBridge {

    CompletableFuture<?> handOff(PipelineItem item, PipelineItem.ExecutionStage failedStage);
}
