package com.example.tasktracker.emailsender.o11y.observation.util;

import com.example.tasktracker.emailsender.pipeline.model.PipelineItem;
import com.example.tasktracker.emailsender.util.AsyncUtils;
import io.micrometer.observation.Observation;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Slf4j
public class TelemetryTracker {

    /**
     * Оборачивает асинхронный вызов в Observation. Обрабатывает исключения и статус PipelineItem.
     */
    public CompletableFuture<Void> trackAsync(
            Observation observation,
            PipelineItem item,
            Supplier<CompletableFuture<Void>> action) {

        observation.start();

        try (Observation.Scope ignored = observation.openScope()) {
            return action.get().whenComplete((result, ex) -> {
                if (ex != null) {
                    observation.error(AsyncUtils.unwrap(ex));
                } else {
                    reportIfFailed(observation, item);
                }
                observation.stop();
            });
        } catch (Throwable t) {
            observation.error(t);
            observation.stop();
            return CompletableFuture.failedFuture(t);
        }
    }

    private void reportIfFailed(Observation observation, PipelineItem item) {
        item.getStage().toException().ifPresent(observation::error);
    }
}
